package org.osiam.web.controller

import org.osiam.helper.HttpClientHelper
import org.osiam.helper.HttpClientRequestResult
import org.osiam.helper.ObjectMapperWithExtensionConfig
import org.osiam.resources.scim.Extension
import org.osiam.resources.scim.MultiValuedAttribute
import org.osiam.resources.scim.User
import org.osiam.web.util.HttpHeader
import org.osiam.web.util.MailSenderBean
import org.osiam.web.util.RegistrationExtensionUrnProvider
import org.osiam.web.util.ResourceServerUriBuilder
import org.springframework.http.HttpStatus
import spock.lang.Specification

import javax.servlet.ServletContext
import javax.servlet.ServletOutputStream
import javax.servlet.http.HttpServletResponse

/**
 * Test for LostPasswordController
 * User: Jochen Todea
 * Date: 15.11.13
 * Time: 14:41
 * Created: with Intellij IDEA
 */
class LostPasswordControllerTest extends Specification {

    def mapper = new ObjectMapperWithExtensionConfig()
    def httpClientMock = Mock(HttpClientHelper)
    def requestResultMock = Mock(HttpClientRequestResult)
    def contextMock = Mock(ServletContext)

    def registrationExtensionUrnProvider = Mock(RegistrationExtensionUrnProvider)
    def resourceServerUriBuilder = Mock(ResourceServerUriBuilder)

    def urn = "urn:scim:schemas:osiam:1.0:Registration"

    def oneTimePasswordField = "oneTimePassword"

    def mailSenderMock = Mock(MailSenderBean)
    def passwordlostLinkPrefix = "http://localhost:8080"
    def passwordlostMailFrom = "noreply@example.org"
    def passwordlostMailSubject = "Subject"

    def clientPasswordChangeUri = "http://localhost:8080"

    def lostPasswordController = new LostPasswordController(httpClient: httpClientMock, oneTimePassword: oneTimePasswordField,
            context: contextMock, mailSender: mailSenderMock, passwordlostLinkPrefix: passwordlostLinkPrefix,
            passwordlostMailFrom: passwordlostMailFrom, passwordlostMailSubject: passwordlostMailSubject,
            registrationExtensionUrnProvider: registrationExtensionUrnProvider, resourceServerUriBuilder: resourceServerUriBuilder,
            clientPasswordChangeUri: clientPasswordChangeUri, mapper: mapper)


    def "The controller should start the flow by generating a one time password and send an email to the user"() {
        given:
        def userId = "someId"
        def authZHeader = "Bearer ACCESSTOKEN"

        def uri = "http://localhost:8080/osiam-resource-server/Users/"+ userId
        def userString = getUserAsStringWithExtension("token")

        def inputStream = new ByteArrayInputStream('nine bytes and one placeholder $PASSWORDLOSTURL'.bytes)

        when:
        def result = lostPasswordController.lost(authZHeader, userId)

        then:
        1 * resourceServerUriBuilder.buildUsersUriWithUserId(userId) >> uri
        1 * registrationExtensionUrnProvider.getExtensionUrn() >> urn
        1 * httpClientMock.executeHttpGet(uri, HttpHeader.AUTHORIZATION, authZHeader) >> requestResultMock
        1 * httpClientMock.executeHttpPatch(uri, _, HttpHeader.AUTHORIZATION, authZHeader) >> new HttpClientRequestResult("body", 200);

        1 * requestResultMock.getStatusCode() >> 200
        1 * requestResultMock.getBody() >> userString

        1 * mailSenderMock.getEmailContentAsStream("/WEB-INF/registration/passwordlostmail-content.txt", _, contextMock) >> inputStream
        1 * mailSenderMock.sendMail("noreply@example.org", "toemail@example.org", "Subject", inputStream, _)
        1 * mailSenderMock.extractPrimaryEmail(_) >> "toemail@example.org"

        result.getStatusCode() == HttpStatus.OK
    }

    def "there should be an failure if retrieving the user by his id failed"(){
        given:
        def userId = "someId"
        def authZHeader = "Bearer ACCESSTOKEN"
        def uri = "http://localhost:8080/osiam-resource-server/Users/" + userId

        when:
        def response = lostPasswordController.lost(authZHeader, userId)

        then:
        1 * resourceServerUriBuilder.buildUsersUriWithUserId(userId) >> uri
        1 * httpClientMock.executeHttpGet(uri, HttpHeader.AUTHORIZATION, authZHeader) >> new HttpClientRequestResult('', 400)
        response.getStatusCode() == HttpStatus.BAD_REQUEST
    }

    def "there should be an failure if the user could not be updated with one time password"(){
        given:
        def userId = "someId"
        def authZHeader = "Bearer ACCESSTOKEN"
        def uri = "http://localhost:8080/osiam-resource-server/Users/" + userId
        def userString = getUserAsStringWithExtension("token")

        when:
        def response = lostPasswordController.lost(authZHeader, userId)

        then:
        1 * resourceServerUriBuilder.buildUsersUriWithUserId(userId) >> uri
        1 * httpClientMock.executeHttpGet(uri, HttpHeader.AUTHORIZATION, authZHeader) >> requestResultMock
        1 * requestResultMock.getStatusCode() >> 200
        1 * requestResultMock.getBody() >> userString
        1 * registrationExtensionUrnProvider.getExtensionUrn() >> urn
        1 * httpClientMock.executeHttpPatch(uri, _, HttpHeader.AUTHORIZATION, authZHeader) >> new HttpClientRequestResult("body", 400);
        response.getStatusCode() == HttpStatus.BAD_REQUEST
    }

    def "there should be an failure if no primary email was found"(){
        given:
        def userId = "someId"
        def authZHeader = "Bearer ACCESSTOKEN"
        def uri = "http://localhost:8080/osiam-resource-server/Users/" + userId
        def userString = getUserAsStringWithExtension("token")

        when:
        def response = lostPasswordController.lost(authZHeader, userId)

        then:
        1 * resourceServerUriBuilder.buildUsersUriWithUserId(userId) >> uri
        1 * httpClientMock.executeHttpGet(uri, HttpHeader.AUTHORIZATION, authZHeader) >> requestResultMock
        1 * requestResultMock.getStatusCode() >> 200
        1 * requestResultMock.getBody() >> userString
        1 * registrationExtensionUrnProvider.getExtensionUrn() >> urn
        1 * httpClientMock.executeHttpPatch(uri, _, HttpHeader.AUTHORIZATION, authZHeader) >> new HttpClientRequestResult("body", 200);
        1 * mailSenderMock.extractPrimaryEmail(_) >> null
        response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR
        response.getBody() != null
    }

    def "there should be an failure if the email content for confirmation mail was not found"(){
        given:
        def userId = "someId"
        def authZHeader = "Bearer ACCESSTOKEN"
        def uri = "http://localhost:8080/osiam-resource-server/Users/" + userId
        def userString = getUserAsStringWithExtension("token")

        when:
        def response = lostPasswordController.lost(authZHeader, userId)

        then:
        1 * resourceServerUriBuilder.buildUsersUriWithUserId(userId) >> uri
        1 * httpClientMock.executeHttpGet(uri, HttpHeader.AUTHORIZATION, authZHeader) >> requestResultMock
        1 * requestResultMock.getStatusCode() >> 200
        1 * requestResultMock.getBody() >> userString
        1 * registrationExtensionUrnProvider.getExtensionUrn() >> urn
        1 * httpClientMock.executeHttpPatch(uri, _, HttpHeader.AUTHORIZATION, authZHeader) >> new HttpClientRequestResult("body", 200);
        1 * mailSenderMock.extractPrimaryEmail(_) >> "primary@mail.com"
        1 * mailSenderMock.getEmailContentAsStream("/WEB-INF/registration/passwordlostmail-content.txt", _, contextMock) >> null
        response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR
        response.getBody() != null
    }

    def "The controller should verify the user and change its password"() {
        given:
        def otp = "someOTP"
        def userId = "someId"
        def newPassword = "newPassword"
        def authZHeader = "Bearer ACCESSTOKEN"
        def uri = "http://localhost:8080/osiam-resource-server/Users/" + userId

        def userById = getUserAsStringWithExtension(otp)

        when:
        def result = lostPasswordController.change(authZHeader, otp, userId, newPassword)

        then:
        1 * resourceServerUriBuilder.buildUsersUriWithUserId(userId) >> uri
        1 * httpClientMock.executeHttpGet(uri, HttpHeader.AUTHORIZATION, authZHeader) >> requestResultMock
        1 * requestResultMock.getStatusCode() >> 200
        1 * requestResultMock.getBody() >> userById
        1 * registrationExtensionUrnProvider.getExtensionUrn() >> urn
        1 * httpClientMock.executeHttpPatch(uri, _, HttpHeader.AUTHORIZATION, authZHeader) >> requestResultMock
        1 * requestResultMock.getStatusCode() >> 200
        1 * requestResultMock.getBody() >> "updated user"

        result.getStatusCode() == HttpStatus.OK
        result.getBody() == "updated user"
    }

    def "If the user will not be found by his id the response should contain the appropriate status code"() {
        given:
        def otp = "someOTP"
        def userId = "someId"
        def newPassword = "newPassword"
        def authZHeader = "Bearer ACCESSTOKEN"
        def uri = "http://localhost:8080/osiam-resource-server/Users/" + userId

        when:
        def result = lostPasswordController.change(authZHeader, otp, userId, newPassword)

        then:
        1 * resourceServerUriBuilder.buildUsersUriWithUserId(userId) >> uri
        1 * httpClientMock.executeHttpGet(uri, HttpHeader.AUTHORIZATION, authZHeader) >> requestResultMock
        2 * requestResultMock.getStatusCode() >> 400

        result.getStatusCode() == HttpStatus.BAD_REQUEST
    }

    def "If the provided one time password has no match with the saved one from the database the appropriate status code will be returned and the process is stopped"() {
        given:
        def otp = "someOTP"
        def userId = "someId"
        def newPassword = "newPassword"
        def authZHeader = "Bearer ACCESSTOKEN"
        def uri = "http://localhost:8080/osiam-resource-server/Users/" + userId

        def userById = getUserAsStringWithExtension("Invalid OTP")

        when:
        def result = lostPasswordController.change(authZHeader, otp, userId, newPassword)

        then:
        1 * resourceServerUriBuilder.buildUsersUriWithUserId(userId) >> uri
        1 * httpClientMock.executeHttpGet(uri, HttpHeader.AUTHORIZATION, authZHeader) >> requestResultMock
        1 * requestResultMock.getStatusCode() >> 200
        1 * requestResultMock.getBody() >> userById
        1 * registrationExtensionUrnProvider.getExtensionUrn() >> urn
        result.getStatusCode() == HttpStatus.FORBIDDEN
    }

    def "there should be a failure if the user update with extensions failed"() {
        given:
        def otp = "someOTP"
        def userId = "someId"
        def newPassword = "newPassword"
        def authZHeader = "Bearer ACCESSTOKEN"
        def uri = "http://localhost:8080/osiam-resource-server/Users/" + userId

        def userById = getUserAsStringWithExtension("someOTP")

        when:
        def result = lostPasswordController.change(authZHeader, otp, userId, newPassword)

        then:
        1 * resourceServerUriBuilder.buildUsersUriWithUserId(userId) >> uri
        1 * httpClientMock.executeHttpGet(uri, HttpHeader.AUTHORIZATION, authZHeader) >> requestResultMock
        1 * requestResultMock.getStatusCode() >> 200
        1 * requestResultMock.getBody() >> userById
        1 * registrationExtensionUrnProvider.getExtensionUrn() >> urn
        1 * httpClientMock.executeHttpPatch(uri, _, HttpHeader.AUTHORIZATION, authZHeader) >> requestResultMock
        2 * requestResultMock.getStatusCode() >> 400
        result.getStatusCode() == HttpStatus.BAD_REQUEST
        result.getBody() != null
    }

    def "there should be a failure if the provided one time password is empty"() {
        when:
        def result = lostPasswordController.change("authZ", "", "userId", "newPW")

        then:
        result.getStatusCode() == HttpStatus.UNAUTHORIZED
    }

    def "The controller should provide a html form for entering the new password with already known values like otp and user id"(){
        given:
        def servletResponseMock = Mock(HttpServletResponse)
        def servletResponseOutputStream = Mock(ServletOutputStream)
        def otp = "otp"
        def userId = "userID"

        def inputStream = new ByteArrayInputStream("some html with placeholder \$CHANGELINK, \$OTP, \$USERID".bytes)

        when:
        lostPasswordController.lostForm(otp, userId, servletResponseMock)

        then:
        1 * contextMock.getResourceAsStream("/WEB-INF/registration/change_password.html") >> inputStream
        1 * servletResponseMock.getOutputStream() >> servletResponseOutputStream
    }

    def getUserAsStringWithExtension(String otp) {
        def emails = new MultiValuedAttribute(primary: true, value: "email@example.org")

        Extension extension = new Extension(urn)
        extension.addOrUpdateField("oneTimePassword", otp)

        def user = new User.Builder("George")
                .setPassword("password")
                .setEmails([emails])
                .addExtension(extension)
                .setActive(false)
                .build()

        return mapper.writeValueAsString(user)
    }
}