// Copyright 2015 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.adaptor.documentum;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Collections.singletonList;

import com.google.enterprise.adaptor.Acl;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.Principal;
import com.google.enterprise.adaptor.documentum.DocumentumAdaptor.CaseSensitivityType;
import com.google.enterprise.adaptor.documentum.DocumentumAdaptor.Checkpoint;

import com.documentum.com.IDfClientX;
import com.documentum.fc.client.DfIdNotFoundException;
import com.documentum.fc.client.IDfACL;
import com.documentum.fc.client.IDfCollection;
import com.documentum.fc.client.IDfPermitType;
import com.documentum.fc.client.IDfQuery;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.common.DfException;
import com.documentum.fc.common.DfId;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Returns all Documentum ACLs in map with doc id as key and Acl as value.
 *
 * Documentum ACLs are modeled as a chain of between 1 and N + 2 GSA
 * ACLs, one ACL for each required group (N, where N >= 0), one for
 * the required group set (0 or 1), and one for the basic permit and
 * restricted permissions (1).
 */
class DocumentumAcls {
  private static Logger logger =
      Logger.getLogger(DocumentumAcls.class.getName());

  private enum State { GET_ACLS, GET_UPDATE_ACLS };

  private State state = null;

  private final IDfClientX dmClientX;
  private final IDfSession dmSession;
  private final Principals principals;
  private final CaseSensitivityType caseSensitivityType;

  private Checkpoint aclCheckpoint;

  DocumentumAcls(IDfClientX dmClientX, IDfSession dmSession,
      Principals principals, CaseSensitivityType caseSensitivityType) {
    this.dmClientX = checkNotNull(dmClientX, "dmClientX may not be null");
    this.dmSession = checkNotNull(dmSession, "dmSession may not be null");
    this.principals = checkNotNull(principals, "principals may not be null");
    this.caseSensitivityType = caseSensitivityType;
  }

  private IDfQuery makeAclQuery(String aclId, int batchSize) {
    IDfQuery query = dmClientX.getQuery();
    StringBuilder queryStr = new StringBuilder()
      .append("SELECT r_object_id FROM dm_acl");
    if (aclId != null) {
      queryStr.append(" WHERE r_object_id > '").append(aclId).append("'");
    }
    queryStr.append(" ORDER BY r_object_id");
    if (batchSize > 0) {
      queryStr.append(" ENABLE(RETURN_TOP ").append(batchSize).append(")");
    }
    logger.log(Level.FINER, "ACL query: {0}", queryStr);
    query.setDQL(queryStr.toString());
    return query;
  }

  private IDfQuery makeUpdateAclQuery(Checkpoint checkpoint, int batchSize)
      throws DfException {
    //TODO(sveldurthi): Create utility function for getServerVersion().matches()
    String dateToStringFunction =
        dmSession.getServerVersion().matches("[456]\\..*")
        ? "DATETOSTRING" : "DATETOSTRING_LOCAL";
    StringBuilder queryStr = new StringBuilder()
        .append("SELECT r_object_id, audited_obj_id, ")
        .append("event_name, time_stamp_utc, ")
        .append(dateToStringFunction)
        .append("(time_stamp_utc, 'yyyy-mm-dd hh:mi:ss') ")
        .append("AS time_stamp_utc_str ")
        .append("FROM dm_audittrail_acl ")
        .append("WHERE (event_name='dm_save' OR event_name='dm_saveasnew' ")
        .append("OR event_name='dm_destroy')");

    String whereBoundedClause = " and ((time_stamp_utc = "
        + "DATE(''{0}'',''yyyy-mm-dd hh:mi:ss'') AND (r_object_id > ''{1}'')) "
        + "OR (time_stamp_utc > DATE(''{0}'',''yyyy-mm-dd hh:mi:ss'')))";

    Object[] arguments =
        { checkpoint.getLastModified(), checkpoint.getObjectId() };
    queryStr.append(MessageFormat.format(whereBoundedClause, arguments));
    queryStr.append(" ORDER BY time_stamp_utc, r_object_id");
    if (batchSize > 0) {
      queryStr.append(" ENABLE(RETURN_TOP ").append(batchSize).append(")");
    }
    logger.log(Level.FINER, "Update ACL query: {0}", queryStr);

    IDfQuery query = dmClientX.getQuery();
    query.setDQL(queryStr.toString());
    return query;
  }

  /**
   * Returns Documentum ACLs in map with doc id and Acl.
   * 
   * In Documentum, ACLs are high level objects separate from content objects.
   * An ACL can be applied to one or many content objects. Or, each object 
   * can have its own individual ACL applied to it. So need to send all
   * the ACLs in Documentum to GSA.
   * 
   * @param checkpoint the last checkpoint
   * @param batchSize number of ACLs to fetch in each query
   * @param aclMap a map of Adaptor Acls for all Documentum ACLs
   * @return {@code true} if the traversal is complete,
   *     or {@code false} otherwise
   * @throws DfException if error in getting ACL information.
   */
  public boolean getAcls(Checkpoint checkpoint, int batchSize,
      Map<DocId, Acl> aclMap) throws DfException {
    checkState(state != State.GET_UPDATE_ACLS,
        "Only one of getAcls or getUpdateAcls may be called.");
    state = State.GET_ACLS;

    aclCheckpoint = checkpoint;
    IDfQuery query = makeAclQuery(checkpoint.getObjectId(), batchSize);
    IDfCollection dmAclCollection =
        query.execute(dmSession, IDfQuery.DF_EXECREAD_QUERY);
    try {
      boolean isComplete = true;
      while (dmAclCollection.next()) {
        isComplete = (batchSize == 0);
        String objectId = dmAclCollection.getString("r_object_id");
        logger.log(Level.FINE, "ACL ID: {0}", objectId);
        try {
          IDfACL dmAcl = (IDfACL) dmSession.getObject(new DfId(objectId));
          addAclChainToMap(dmAcl, objectId, aclMap);
        } catch (DfIdNotFoundException e) {
          logger.log(Level.FINE,
              "Skipping ACL {0}: {1}", new Object[] {objectId, e});
        }
        aclCheckpoint = new Checkpoint(objectId);
      }
      if (isComplete) {
        // for a successful completion, reset to full.
        aclCheckpoint = Checkpoint.full();
      }
      return isComplete;
    } finally {
      try {
        dmAclCollection.close();
      } catch (DfException e) {
        logger.log(Level.WARNING, "Error closing collection", e);
      }
    }
  }

  /**
   * Returns updated Documentum ACLs in map with doc id and Acl.
   *
   * In Documentum, ACLs are high level objects separate from content objects.
   * An ACL can be applied to one or many content objects. Or, each object
   * can have it's own individual ACL applied to it. So this method needs to
   * send all the ACLs in Documentum to GSA.
   *
   * @param checkpoint the last checkpoint
   * @param batchSize number of ACLs to fetch in each query
   * @param aclMap a map of Adaptor Acls for all Documentum ACLs.
   * @return {@code true} if the traversal is complete,
   *     or {@code false} otherwise
   * @throws DfException if error in getting ACL information.
   */
  public boolean getUpdateAcls(Checkpoint checkpoint, int batchSize,
      Map<DocId, Acl> aclMap) throws DfException {
    checkState(state != State.GET_ACLS,
        "Only one of getAcls or getUpdateAcls may be called.");
    state = State.GET_UPDATE_ACLS;

    aclCheckpoint = checkpoint;
    HashSet<String> aclModifiedIds = new HashSet<String>();
    String aclModifiedDate = checkpoint.getLastModified();
    String aclModifyId = checkpoint.getObjectId();
    IDfQuery query = makeUpdateAclQuery(checkpoint, batchSize);
    IDfCollection dmAclCollection =
        query.execute(dmSession, IDfQuery.DF_EXECREAD_QUERY);
    try {
      boolean isComplete = true;
      while (dmAclCollection.next()) {
        isComplete = (batchSize == 0);
        aclModifiedDate = dmAclCollection.getString("time_stamp_utc_str");
        aclModifyId = dmAclCollection.getString("r_object_id");
        String modifyObjectId = dmAclCollection.getString("audited_obj_id");
        String eventName = dmAclCollection.getString("event_name");
        logger.log(Level.FINE, "Updated ACL ID: {0}, event: {1}",
            new String[] {modifyObjectId, eventName});

        if (aclModifiedIds.contains(modifyObjectId)) {
          logger.log(Level.FINER,
              "Skipping ACL {0}: redundant event", modifyObjectId);
        } else {
          aclModifiedIds.add(modifyObjectId);
          if ("dm_destroy".equalsIgnoreCase(eventName)) {
            aclMap.put(new DocId(modifyObjectId), Acl.EMPTY);
          } else {
            try {
              IDfACL dmAcl =
                  (IDfACL) dmSession.getObject(new DfId(modifyObjectId));
              addAclChainToMap(dmAcl, modifyObjectId, aclMap);
            } catch (DfIdNotFoundException e) {
              logger.log(Level.FINER,
                  "Deleting ACL {0}: {1}", new Object[] {modifyObjectId, e});
              aclMap.put(new DocId(modifyObjectId), Acl.EMPTY);
            }
          }
        }
        aclCheckpoint = new Checkpoint(aclModifiedDate, aclModifyId);
      }
      return isComplete;
    } finally {
      try {
        dmAclCollection.close();
      } catch (DfException e) {
        logger.log(Level.WARNING, "Error closing collection", e);
      }
    }
  }

  /**
   * Returns the Checkpoint for the last ACL processed in the last
   * call to {@link #getAcls} or {@link getUpdateAcls}.
   */
  public Checkpoint getCheckpoint() {
    checkState(state != null,
        "One of getAcls or getUpdateAcls must be called before getCheckpoint.");

    return aclCheckpoint;
  }

  /**
   * Adds all of the Adaptor Acls for the Documentum ACL to the map.
   *
   * @param dmAcl Documentum ACL object.
   * @param objectId Documentum ACL object id.
   * @param aclMap Map with doc id and acl.
   * @throws DfException if error in getting acl info.
   */
  private void addAclChainToMap(IDfACL dmAcl, String objectId,
      Map<DocId, Acl> aclMap) throws DfException {
    List<String> requiredGroupSet = new ArrayList<String>();
    String parentAclId = null;
    Set<Principal> permits = new HashSet<Principal>();
    Set<Principal> denies = new HashSet<Principal>();

    for (int i = 0; i < dmAcl.getAccessorCount(); i++) {
      String accessorName = dmAcl.getAccessorName(i);
      int permitType = dmAcl.getAccessorPermitType(i);

      if (permitType == IDfPermitType.REQUIRED_GROUP) {
        String aclId = objectId + "_" + accessorName;
        Acl acl = getRequiredAcl(parentAclId, singletonList(accessorName));
        aclMap.put(new DocId(aclId), acl);
        parentAclId = aclId;
      } else if (permitType == IDfPermitType.REQUIRED_GROUP_SET) {
        requiredGroupSet.add(accessorName);
      } else {
        processBasicPermissions(accessorName, permitType,
            dmAcl.getAccessorPermit(i), dmAcl.isGroup(i), permits, denies);
      }
    }

    if (!requiredGroupSet.isEmpty()) {
      String aclId = objectId + "_reqGroupSet";
      Acl acl = getRequiredAcl(parentAclId, requiredGroupSet);
      aclMap.put(new DocId(aclId), acl);
      parentAclId = aclId;
    }

    if (parentAclId != null) {
      logger.log(Level.FINE,
          "ACL {0} has required groups or required group set", objectId);
    }
    Acl acl = getBasicAcl(parentAclId, permits, denies,
        Acl.InheritanceType.PARENT_OVERRIDES);
    aclMap.put(new DocId(objectId), acl);
  }

  /**
   * Creates an Adaptor Acl object with required groups or required group sets.
   *
   * @param parentAclId the doc ID of the parent ACL in the chain
   * @param groups the group names
   * @return Adaptor Acl object.
   * @throws DfException if error in getting acl info.
   */
  private Acl getRequiredAcl(String parentAclId, List<String> groups)
      throws DfException {
    Set<Principal> permits = new HashSet<Principal>();
    for (String name : groups) {
      Principal principal = principals.getPrincipal(name, true);
      if (principal != null) {
        permits.add(principal);
      }
    }
    return getBasicAcl(parentAclId, permits, new HashSet<Principal>(),
        Acl.InheritanceType.AND_BOTH_PERMIT);
  }

  /**
   * Creates a principal for an ACL entry with basic permissions.
   * Populates permits set with users and groups with READ permission.
   * Populates denies set with users and groups with no READ
   * permission.
   *
   * @param accessorName the ACL entry accessor name
   * @param permitType the type of the ACL entry (required group,
   *     access permit, etc.)
   * @param accessorPermit the access permission of the ACL entry
   * @param isGroup {@code true} iff the accessor is a group
   * @param permits the set of permitted principals to populate
   * @param denies the set of denied principals to populate
   * @throws DfException if error in getting user or group information.
   */
  private void processBasicPermissions(String accessorName, int permitType,
      int accessorPermit, boolean isGroup, Set<Principal> permits,
      Set<Principal> denies) throws DfException {
    if (permitType == IDfPermitType.ACCESS_RESTRICTION) {
      if (accessorPermit <= IDfACL.DF_PERMIT_READ) {
        Principal principal = principals.getPrincipal(accessorName, isGroup);
        if (principal != null) {
          denies.add(principal);
        }
      }
    } else if (permitType == IDfPermitType.ACCESS_PERMIT) {
      if (accessorPermit >= IDfACL.DF_PERMIT_READ) {
        if (accessorName.equalsIgnoreCase("dm_owner")
            || accessorName.equalsIgnoreCase("dm_group")) {
          // skip dm_owner and dm_group for now.
          // TODO (Srinivas): Need to resolve these acls for
          //      both allow and deny.
        } else {
          Principal principal = principals.getPrincipal(accessorName, isGroup);
          if (principal != null) {
            permits.add(principal);
          }
        }
      }
    }
  }

  /*
   * Creates an Adaptor Acl for the basic permissions in the ACL
   * object.
   *
   * @param parentAclId the doc ID of the parent ACL in the chain
   * @param permits the principals permitted access
   * @param denies the principals denied access
   * @param inheritanceType inheritance type for the Acl
   * @return Adaptor Acl object
   * @throws DfException if error in getting user or group information.
   */
  private Acl getBasicAcl(String parentAclId,
      Set<Principal> permits, Set<Principal> denies,
      Acl.InheritanceType inheritanceType) throws DfException {
    Acl.Builder builder = new Acl.Builder()
        .setPermits(permits)
        .setDenies(denies)
        .setInheritanceType(inheritanceType);
    if (caseSensitivityType
        .equals(CaseSensitivityType.EVERYTHING_CASE_SENSITIVE)) {
      builder.setEverythingCaseSensitive();
    } else {
      builder.setEverythingCaseInsensitive();
    }
    if (parentAclId != null) {
      builder.setInheritFrom(new DocId(parentAclId));
    }
    return builder.build();
  }
}
