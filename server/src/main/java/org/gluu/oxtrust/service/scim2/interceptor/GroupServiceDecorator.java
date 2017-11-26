package org.gluu.oxtrust.service.scim2.interceptor;

import org.apache.commons.lang.StringUtils;
import org.gluu.oxtrust.ldap.service.IGroupService;
import org.gluu.oxtrust.model.GluuGroup;
import org.gluu.oxtrust.model.exception.SCIMException;
import org.gluu.oxtrust.model.scim2.ErrorScimType;
import org.gluu.oxtrust.model.scim2.patch.PatchRequest;
import org.gluu.oxtrust.model.scim2.SearchRequest;
import org.gluu.oxtrust.model.scim2.group.GroupResource;
import org.gluu.oxtrust.ws.rs.scim2.BaseScimWebService;
import org.gluu.oxtrust.ws.rs.scim2.GroupService;
import org.slf4j.Logger;

import javax.annotation.Priority;
import javax.decorator.Decorator;
import javax.decorator.Delegate;
import javax.enterprise.inject.Any;
import javax.inject.Inject;
import javax.interceptor.Interceptor;
import javax.ws.rs.core.Response;

/**
 * Aims at decorating SCIM group service methods. Currently applies validations via ResourceValidator class or other custom
 * validation logic
 *
 * Created by jgomer on 2017-10-18.
 */
@Priority(Interceptor.Priority.APPLICATION)
@Decorator
public class GroupServiceDecorator extends BaseScimWebService implements GroupService {

    @Inject
    private Logger log;

    @Inject @Delegate @Any
    GroupService service;

    @Inject
    private IGroupService groupService;

    private Response validateExistenceOfGroup(String id){

        Response response=null;
        GluuGroup group = StringUtils.isEmpty(id) ? null : groupService.getGroupByInum(id);

        if (group==null) {
            log.info("Group with inum {} not found", id);
            response = getErrorResponse(Response.Status.NOT_FOUND, "Resource " + id + " not found");
        }
        return response;

    }

    public Response createGroup(GroupResource group, String attrsList, String excludedAttrsList){

        Response response;
        try {
            //empty externalId, no place to store it in LDAP
            group.setExternalId(null);

            executeDefaultValidation(group);
            assignMetaInformation(group);
            //Proceed with actual implementation of createGroup method
            response=service.createGroup(group, attrsList, excludedAttrsList);
        }
        catch (SCIMException e){
            log.error("Validation check at createGroup returned: {}", e.getMessage());
            response = getErrorResponse(Response.Status.BAD_REQUEST, ErrorScimType.INVALID_VALUE, e.getMessage());
        }
        return response;

    }

    public Response getGroupById(String id, String attrsList, String excludedAttrsList){

        Response response=validateExistenceOfGroup(id);
        if (response==null)
            //Proceed with actual implementation of getGroupById method
            response=service.getGroupById(id, attrsList, excludedAttrsList);

        return response;

    }

    public Response updateGroup(GroupResource group, String id, String attrsList, String excludedAttrsList){

        Response response;
        try{
            //empty externalId, no place to store it in LDAP
            group.setExternalId(null);

            //Check if the ids match in case the group coming has one
            if (group.getId()!=null && !group.getId().equals(id))
                throw new SCIMException("Parameter id does not match with id attribute of Group");

            response=validateExistenceOfGroup(id);
            if (response==null) {
                executeDefaultValidation(group);
                //Proceed with actual implementation of updateGroup method
                response = service.updateGroup(group, id, attrsList, excludedAttrsList);
            }
        }
        catch (SCIMException e){
            log.error("Validation check at updateGroup returned: {}", e.getMessage());
            response = getErrorResponse(Response.Status.BAD_REQUEST, ErrorScimType.INVALID_VALUE, e.getMessage());
        }
        return response;
    }

    public Response deleteGroup(String id){

        Response response=validateExistenceOfGroup(id);
        if (response==null)
            //Proceed with actual implementation of deleteGroup method
            response= service.deleteGroup(id);

        return response;

    }

    public Response searchGroups(String filter, String sortBy, String sortOrder, Integer startIndex, Integer count,
                                 String attrsList, String excludedAttrsList){

        SearchRequest searchReq=new SearchRequest();
        Response response=prepareSearchRequest(searchReq.getSchemas(), filter, sortBy, sortOrder, startIndex, count,
                            attrsList, excludedAttrsList, searchReq, "displayName");

        if (response==null) {
            //searchReq.getSortBy() is not null since we are providing userName as default
            if (!isAttributeRecognized(GroupResource.class, searchReq.getSortBy()))
                response = getErrorResponse(Response.Status.BAD_REQUEST, ErrorScimType.INVALID_PATH, "sortBy parameter value not recognized");
            else
                response = service.searchGroups(searchReq.getFilter(), searchReq.getSortBy(), searchReq.getSortOrder(),
                        searchReq.getStartIndex(), searchReq.getCount(), searchReq.getAttributes(),
                        searchReq.getExcludedAttributes());
        }
        return response;

    }

    public Response searchGroupsPost(SearchRequest searchRequest){

        SearchRequest searchReq=new SearchRequest();
        Response response=prepareSearchRequest(searchRequest.getSchemas(), searchRequest.getFilter(), searchRequest.getSortBy(),
                searchRequest.getSortOrder(), searchRequest.getStartIndex(), searchRequest.getCount(),
                searchRequest.getAttributes(), searchRequest.getExcludedAttributes(), searchReq, "displayName");

        if (response==null) {
            //searchReq.getSortBy() is not null since we are providing userName as default
            if (!isAttributeRecognized(GroupResource.class, searchReq.getSortBy()))
                response = getErrorResponse(Response.Status.BAD_REQUEST, ErrorScimType.INVALID_PATH, "sortBy parameter value not recognized");
            else
                response = service.searchGroupsPost(searchReq);
        }
        return response;

    }

    public Response patchGroup(PatchRequest request, String id, String attrsList, String excludedAttrsList){

        Response response=inspectPatchRequest(request, GroupResource.class);
        if (response==null) {
            response=validateExistenceOfGroup(id);

            if (response==null)
                response = service.patchGroup(request, id, attrsList, excludedAttrsList);
        }
        return response;

    }

}
