package de.tum.in.www1.artemis.web.rest.open;

import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.glassfish.jersey.uri.UriComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.nimbusds.jwt.SignedJWT;

import de.tum.in.www1.artemis.security.annotations.EnforceNothing;

/**
 * REST controller for receiving LTI requests.
 */
@Profile("lti")
@RestController
// TODO: should we adapt the mapping based on the profile?
@RequestMapping("api/public/")
public class PublicLtiResource {

    private static final Logger log = LoggerFactory.getLogger(PublicLtiResource.class);

    public static final String LOGIN_REDIRECT_CLIENT_PATH = "/lti/launch";

    /**
     * POST lti13/auth-callback Redirects an LTI 1.3 Authorization Request Response to the client
     *
     * @param request  HTTP request
     * @param response HTTP response
     * @throws IOException If an input or output exception occurs
     */
    @PostMapping("lti13/auth-callback")
    @EnforceNothing
    public void lti13LaunchRedirect(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String state = request.getParameter("state");
        if (state == null) {
            errorOnMissingParameter(response, "state");
            return;
        }

        String idToken = request.getParameter("id_token");
        if (idToken == null) {
            errorOnMissingParameter(response, "id_token");
            return;
        }

        if (!isValidJwtIgnoreSignature(idToken)) {
            errorOnIllegalParameter(response);
            return;
        }

        UriComponentsBuilder uriBuilder = buildRedirect(request);
        uriBuilder.path(LOGIN_REDIRECT_CLIENT_PATH);
        uriBuilder.queryParam("state", UriComponent.encode(state, UriComponent.Type.QUERY_PARAM));
        uriBuilder.queryParam("id_token", UriComponent.encode(idToken, UriComponent.Type.QUERY_PARAM));
        String redirectUrl = uriBuilder.build().toString();
        log.info("redirect to url: {}", redirectUrl);
        response.sendRedirect(redirectUrl); // Redirect using user-provided values is safe because user-provided values are used in the query parameters, not the url itself
    }

    /**
     * Strips the signature from a potential JWT and makes sure the rest is valid.
     *
     * @param token The potential token
     * @return Whether the token is valid or not
     */
    private boolean isValidJwtIgnoreSignature(String token) {
        try {
            SignedJWT parsedToken = SignedJWT.parse(token);
            if (parsedToken.getJWTClaimsSet().getExpirationTime().before(Date.from(Instant.now()))) {
                return false;
            }
            return true;
        }
        catch (ParseException e) {
            log.info("LTI request: JWT token is invalid: {}", token, e);
            return false;
        }
    }

    private void errorOnMissingParameter(HttpServletResponse response, String missingParamName) throws IOException {
        String message = "Missing parameter on oauth2 authorization response: " + missingParamName;
        log.error(message);
        response.sendError(HttpStatus.BAD_REQUEST.value(), message);
    }

    private void errorOnIllegalParameter(HttpServletResponse response) throws IOException {
        String message = "Illegal parameter on oauth2 authorization response: id_token";
        log.error(message);
        response.sendError(HttpStatus.BAD_REQUEST.value(), message);
    }

    private UriComponentsBuilder buildRedirect(HttpServletRequest request) {
        UriComponentsBuilder redirectUrlComponentsBuilder = UriComponentsBuilder.newInstance().scheme(request.getScheme()).host(request.getServerName());
        if (request.getServerPort() != 80 && request.getServerPort() != 443) {
            redirectUrlComponentsBuilder.port(request.getServerPort());
        }
        return redirectUrlComponentsBuilder;
    }
}
