package org.gluu.oxtrust.service.scim2.interceptor;

import org.apache.commons.lang.StringUtils;
import org.gluu.oxtrust.ldap.service.IPersonService;
import org.gluu.oxtrust.model.GluuCustomPerson;
import org.gluu.oxtrust.model.exception.SCIMException;
import org.gluu.oxtrust.model.scim2.*;
import org.gluu.oxtrust.model.scim2.patch.PatchRequest;
import org.gluu.oxtrust.model.scim2.user.UserResource;
import org.gluu.oxtrust.model.scim2.util.ScimResourceUtil;
import org.gluu.oxtrust.ws.rs.scim2.BaseScimWebService;
import org.gluu.oxtrust.ws.rs.scim2.UserService;
import org.slf4j.Logger;

import javax.annotation.Priority;
import javax.decorator.Decorator;
import javax.decorator.Delegate;
import javax.enterprise.inject.Any;
import javax.inject.Inject;
import javax.interceptor.Interceptor;
import javax.ws.rs.core.Response;

/**
 * Aims at decorating SCIM user service methods. Currently applies validations via ResourceValidator class or other custom
 * validation logic
 *
 * Created by jgomer on 2017-09-01.
 */
@Priority(Interceptor.Priority.APPLICATION)
@Decorator
public class UserServiceDecorator extends BaseScimWebService implements UserService {

    @Inject
    private Logger log;

    @Inject @Delegate @Any
    UserService service;

    @Inject
    private IPersonService personService;

    private Response validateExistenceOfUser(String id){

        Response response=null;
        GluuCustomPerson person = StringUtils.isEmpty(id) ? null : personService.getPersonByInum(id);

        if (person==null) {
            log.info("Person with inum {} not found", id);
            response = getErrorResponse(Response.Status.NOT_FOUND, "Resource " + id + " not found");
        }
        return response;

    }

    public Response createUser(UserResource user, String attrsList, String excludedAttrsList) {

        Response response;
        try {
            executeDefaultValidation(user);
            assignMetaInformation(user);
            ScimResourceUtil.adjustPrimarySubAttributes(user);
            //Proceed with actual implementation of createUser method
            response = service.createUser(user, attrsList, excludedAttrsList);
        }
        catch (SCIMException e){
            log.error("Validation check at createUser returned: {}", e.getMessage());
            response = getErrorResponse(Response.Status.BAD_REQUEST, ErrorScimType.INVALID_VALUE, e.getMessage());
        }
        return response;

    }

    public Response getUserById(String id, String attrsList, String excludedAttrsList){

        Response response=validateExistenceOfUser(id);
        if (response==null)
            //Proceed with actual implementation of getUserById method
            response= service.getUserById(id, attrsList, excludedAttrsList);

        return response;

    }

    public Response updateUser(UserResource user, String id, String attrsList, String excludedAttrsList) {

        Response response;
        try{
            //Check if the ids match in case the user coming has one
            if (user.getId()!=null && !user.getId().equals(id))
                throw new SCIMException("Parameter id does not match with id attribute of User");

            response=validateExistenceOfUser(id);
            if (response==null) {
                executeDefaultValidation(user);
                //Proceed with actual implementation of updateUser method
                response = service.updateUser(user, id, attrsList, excludedAttrsList);
            }
        }
        catch (SCIMException e){
            log.error("Validation check at updateUser returned: {}", e.getMessage());
            response = getErrorResponse(Response.Status.BAD_REQUEST, ErrorScimType.INVALID_VALUE, e.getMessage());
        }
        return response;

    }

    public Response deleteUser(String id){

        Response response=validateExistenceOfUser(id);
        if (response==null)
            //Proceed with actual implementation of deleteUser method
            response= service.deleteUser(id);

        return response;

    }

    public Response searchUsers(String filter, String sortBy, String sortOrder, Integer startIndex, Integer count,
                                String attrsList, String excludedAttrsList){

        SearchRequest searchReq=new SearchRequest();
        Response response=prepareSearchRequest(searchReq.getSchemas(), filter, sortBy, sortOrder, startIndex, count,
                                attrsList, excludedAttrsList, searchReq, "userName");

        if (response==null) {
            //searchReq.getSortBy() is not null since we are providing userName as default
            if (!isAttributeRecognized(UserResource.class, searchReq.getSortBy()))
                response = getErrorResponse(Response.Status.BAD_REQUEST, ErrorScimType.INVALID_PATH, "sortBy parameter value not recognized");
            else
                response = service.searchUsers(searchReq.getFilter(), searchReq.getSortBy(), searchReq.getSortOrder(),
                                            searchReq.getStartIndex(), searchReq.getCount(), searchReq.getAttributes(),
                                            searchReq.getExcludedAttributes());
        }
        return response;

    }

    public Response searchUsersPost(SearchRequest searchRequest){

        SearchRequest searchReq=new SearchRequest();
        Response response=prepareSearchRequest(searchRequest.getSchemas(), searchRequest.getFilter(), searchRequest.getSortBy(),
                            searchRequest.getSortOrder(), searchRequest.getStartIndex(), searchRequest.getCount(),
                            searchRequest.getAttributes(), searchRequest.getExcludedAttributes(), searchReq, "userName");

        if (response==null) {
            //searchReq.getSortBy() is not null since we are providing userName as default
            if (!isAttributeRecognized(UserResource.class, searchReq.getSortBy()))
                response = getErrorResponse(Response.Status.BAD_REQUEST, ErrorScimType.INVALID_PATH, "sortBy parameter value not recognized");
            else
                response = service.searchUsersPost(searchReq);
        }
        return response;

    }

    public Response patchUser(PatchRequest request, String id, String attrsList, String excludedAttrsList){

        Response response=inspectPatchRequest(request, UserResource.class);
        if (response==null) {
            response=validateExistenceOfUser(id);

            if (response==null)
                response = service.patchUser(request, id, attrsList, excludedAttrsList);
        }
        return response;

    }

}
