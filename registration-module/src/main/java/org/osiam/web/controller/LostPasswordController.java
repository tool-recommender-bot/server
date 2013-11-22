package org.osiam.web.controller;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.osiam.helper.HttpClientHelper;
import org.osiam.helper.HttpClientRequestResult;
import org.osiam.resources.helper.UserDeserializer;
import org.osiam.resources.scim.Extension;
import org.osiam.resources.scim.User;
import org.osiam.web.util.MailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller to handel the lost password flow
 * User: Jochen Todea
 * Date: 15.11.13
 * Time: 14:58
 * Created: with Intellij IDEA
 */
@Controller
@RequestMapping(value = "/password")
public class LostPasswordController {

    private static final Logger LOGGER = Logger.getLogger(LostPasswordController.class.getName());

    private static final String AUTHORIZATION = "Authorization";

    private HttpClientHelper httpClient = new HttpClientHelper();
    private ObjectMapper mapper;

    @Value("${osiam.server.port}")
    private int serverPort;
    @Value("${osiam.server.host}")
    private String serverHost;
    @Value("${osiam.server.http.scheme}")
    private String httpScheme;

    private static final String RESOURCE_SERVER_URI = "/osiam-resource-server/Users";

    @Value("${osiam.internal.scim.extension.urn}")
    private String internalScimExtensionUrn;

    @Value("${osiam.one.time.password.field}")
    private String oneTimePassword;

    @Inject
    ServletContext context;

    private MailSender mailSender = new MailSender();

    @Value("${osiam.web.passwordlostmail.linkprefix}")
    private String passwordlostLinkPrefix;
    @Value("${osiam.web.passwordlostmail.from}")
    private String passwordlostMailFrom;
    @Value("${osiam.web.passwordlostmail.subject}")
    private String passwordlostMailSubject;
    @Value("${osiam.web.passwordlostmail.content.path}")
    private String pathToEmailContent;

    public LostPasswordController() {
        mapper = new ObjectMapper();
        SimpleModule userDeserializerModule = new SimpleModule("userDeserializerModule", new Version(1, 0, 0, null, null, null))
                .addDeserializer(User.class, new UserDeserializer(User.class));
        mapper.registerModule(userDeserializerModule);
    }

    /**
     * This endpoint generates an one time password and send an confirmation email including the one time password to users primary email
     * @param authorization authZ header with valid access token
     * @param userId the user id for whom you want to change the password
     * @return the HTTP status code
     * @throws IOException
     * @throws MessagingException
     */
    @RequestMapping(value = "/lost/{userId}", method = RequestMethod.POST, produces = "application/json")
    public ResponseEntity<String> lost(@RequestHeader final String authorization, @PathVariable final String userId) throws IOException, MessagingException {

        String uri = httpScheme + "://" + serverHost + ":" + serverPort + RESOURCE_SERVER_URI + "/" + userId;

        //get user by his id
        HttpClientRequestResult getResult = httpClient.executeHttpGet(uri, AUTHORIZATION, authorization);

        if (getResult.getStatusCode() != 200) {
            LOGGER.log(Level.WARNING, "Problems getting user by id!");
            return new ResponseEntity<>("{\"error\":\"Problems getting user by id!\"}", HttpStatus.valueOf(getResult.getStatusCode()));
        }

        //generate one time password
        String oneTimePassword = UUID.randomUUID().toString();
        User userForUpdate = buildUserForUpdate(mapper.readValue(getResult.getBody(), User.class), oneTimePassword);

        //update user
        String userAsString = mapper.writeValueAsString(userForUpdate);
        HttpClientRequestResult saveUserResponse = httpClient.executeHttpPatch(uri, userAsString, AUTHORIZATION, authorization);

        if (saveUserResponse.getStatusCode() != 200) {
            LOGGER.log(Level.WARNING, "Problems updating the user with extensions!");
            return new ResponseEntity<>("{\"error\":\"Problems updating the user with extensions!\"}", HttpStatus.valueOf(saveUserResponse.getStatusCode()));
        }

        return sendPasswordLostMail(userForUpdate, oneTimePassword);
    }

    private User buildUserForUpdate(User user, String oneTimePassword) {
        Map<String,String> fields = new HashMap<>();
        fields.put(this.oneTimePassword, oneTimePassword);

        return new User.Builder(user).
                addExtension(internalScimExtensionUrn, new Extension(internalScimExtensionUrn, fields)).build();
    }

    private ResponseEntity<String> sendPasswordLostMail(User parsedUser, String oneTimePassword) throws MessagingException, IOException {

        String primaryEmail = mailSender.extractPrimaryEmail(parsedUser);
        if (primaryEmail == null) {
            LOGGER.log(Level.WARNING, "No primary email found!");
            return new ResponseEntity<>("{\"error\":\"No primary email found!\"}", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        StringBuilder activateURL = new StringBuilder(passwordlostLinkPrefix);
        activateURL.append("userId=").append(parsedUser.getId());
        activateURL.append("&oneTimePassword=").append(oneTimePassword);

        Map<String, String> vars = new HashMap<>();
        vars.put("$PASSWORDLOSTURL", activateURL.toString());

        InputStream mailContentStream =
                mailSender.getEmailContentAsStream("/WEB-INF/registration/passwordlostmail-content.txt",
                        pathToEmailContent, context);

        if (mailContentStream == null) {
            LOGGER.log(Level.SEVERE, "Cant open registermail-content.txt on classpath! Please configure!");
            return new ResponseEntity<>("{\"error\":\"Cant open registermail-content.txt on classpath! Please configure!\"}", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        mailSender.sendMail(passwordlostMailFrom, primaryEmail, passwordlostMailSubject, mailContentStream, vars);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Method to get an HTML form with the appropriate input fields for changing the password
     * @param oneTimePassword the one time password from confirmation email
     * @param userId the user id for whom the password change should be
     * @return HTML form with hidden fields for userId and otp
     */
    @RequestMapping(value = "/lostForm", method = RequestMethod.GET, produces = "text/html")
    public ResponseEntity<String> lostFrom(@RequestParam String oneTimePassword, @RequestParam String userId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /**
     * Method to change the users password if the preconditions are satisfied.
     * @param authorization authZ header with valid access token
     * @param oneTimePassword the previously generated one time password
     * @param userId the user id for whom you want to change the password
     * @param newPassword the new user password
     * @return the response with status code and the updated user if successfully
     * @throws IOException
     */
    @RequestMapping(value = "/change", method = RequestMethod.POST, produces = "application/json")
    public ResponseEntity<String> change(@RequestHeader final String authorization, @RequestParam String oneTimePassword,
                                 @RequestParam String userId, @RequestParam String newPassword) throws IOException {

        if (oneTimePassword.equals("")) {
            LOGGER.log(Level.SEVERE, "The submitted one time password is invalid!");
            return new ResponseEntity<>("{\"error\":\"The submitted one time password is invalid!\"}", HttpStatus.UNAUTHORIZED);
        }

        String uri = httpScheme + "://" + serverHost + ":" + serverPort + RESOURCE_SERVER_URI + "/" + userId;

        //get user by id
        HttpClientRequestResult result = httpClient.executeHttpGet(uri, AUTHORIZATION, authorization);
        if (result.getStatusCode() != 200) {
            LOGGER.log(Level.WARNING, "Problems retrieving user by ID!");
            return new ResponseEntity<>("{\"error\":\"Problems retrieving user by ID!\"}", HttpStatus.valueOf(result.getStatusCode()));
        }
        User user = mapper.readValue(result.getBody(), User.class);

        //validate the oneTimePassword with the saved one from DB
        Extension extension = user.getExtension(internalScimExtensionUrn);
        String savedOTP = extension.getField(this.oneTimePassword);

        if (!savedOTP.equals(oneTimePassword)) {
            LOGGER.log(Level.SEVERE, "The submitted one time password is invalid!");
            return new ResponseEntity<>("{\"error\":\"The submitted one time password is invalid!\"}", HttpStatus.FORBIDDEN);
        }

        //delete the oneTimePassword from user entity
        extension.setField(this.oneTimePassword, "");

        //set new password for the user
        User updateUser = new User.Builder(user).setPassword(newPassword).build();
        String updateUserAsString = mapper.writeValueAsString(updateUser);

        //update the user with PATCH
        HttpClientRequestResult savedResult = httpClient.executeHttpPatch(uri, updateUserAsString, AUTHORIZATION, authorization);

        if (savedResult.getStatusCode() != 200) {
            LOGGER.log(Level.WARNING, "Problems updating the user with extensions!");
            return new ResponseEntity<>("{\"error\":\"Problems updating the user with extensions!\"}", HttpStatus.valueOf(savedResult.getStatusCode()));
        }

        //return saved user with corresponding status code
        return new ResponseEntity<>(savedResult.getBody(), HttpStatus.OK);
    }
}