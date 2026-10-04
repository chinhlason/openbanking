package vn.com.truongsonbank.keycloak.biometric;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class BiometricCredentialResource {
    static final String BIOMETRIC_TYPE = "tsb-biometric";
    static final String PASSKEY_TYPE = "tsb-passkey";
    static final String PIN_TYPE = "tsb-pin";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final KeycloakSession session;

    BiometricCredentialResource(KeycloakSession session) {
        this.session = session;
    }

    @GET
    @Path("credentials")
    @Produces(MediaType.APPLICATION_JSON)
    public Response exists(@QueryParam("username") String username,
                           @QueryParam("deviceId") String deviceId,
                           @QueryParam("credentialType") String credentialType) {
        if (!validSecret()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        UserModel user = user(username);
        return Response.ok(Map.of("exists", user != null && findCredential(user, deviceId, credentialType(credentialType)) != null)).build();
    }

    @POST
    @Path("credentials")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response upsert(Map<String, String> request) {
        if (!validSecret() || blank(request.get("username"))) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        UserModel user = user(request.get("username"));
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String type = credentialType(request.get("credentialType"));
        if (PIN_TYPE.equals(type)) {
            if (blank(request.get("pin"))) {
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }
            upsertPin(user, request.get("pin"));
            return Response.ok(Map.of("enabled", true)).build();
        }
        if (blank(request.get("deviceId")) || blank(request.get("publicKey"))) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        CredentialModel existing = findCredential(user, request.get("deviceId"), type);
        if (existing != null) {
            user.credentialManager().removeStoredCredentialById(existing.getId());
        }
        CredentialModel credential = new CredentialModel();
        String labelPrefix = labelPrefix(type);
        credential.setType(type);
        credential.setUserLabel(labelPrefix + ":" + request.get("deviceId"));
        credential.setCreatedDate(System.currentTimeMillis());
        credential.setCredentialData("{\"deviceId\":\"" + json(request.get("deviceId")) + "\",\"publicKey\":\"" + json(request.get("publicKey")) + "\"}");
        credential.setSecretData("{}");
        user.credentialManager().createStoredCredential(credential);
        return Response.ok(Map.of("enabled", true)).build();
    }

    @DELETE
    @Path("credentials")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(Map<String, String> request) {
        if (!validSecret() || blank(request.get("username")) || blank(request.get("deviceId"))) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        UserModel user = user(request.get("username"));
        CredentialModel credential = user == null ? null : findCredential(user, request.get("deviceId"), credentialType(request.get("credentialType")));
        if (credential != null) {
            user.credentialManager().removeStoredCredentialById(credential.getId());
        }
        return Response.ok(Map.of("enabled", false)).build();
    }

    private UserModel user(String username) {
        return blank(username) ? null : session.users().getUserByUsername(realm(), username);
    }

    static CredentialModel findCredential(UserModel user, String deviceId) {
        return findCredential(user, deviceId, BIOMETRIC_TYPE);
    }

    static CredentialModel findCredential(UserModel user, String deviceId, String type) {
        if (user == null || blank(deviceId)) {
            return null;
        }
        return user.credentialManager().getStoredCredentialsByTypeStream(type)
                .filter(it -> (labelPrefix(type) + ":" + deviceId).equals(it.getUserLabel()))
                .findFirst()
                .orElse(null);
    }

    static String publicKey(CredentialModel credential) {
        if (credential == null || credential.getCredentialData() == null) {
            return "";
        }
        String marker = "\"publicKey\":\"";
        int start = credential.getCredentialData().indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        int end = credential.getCredentialData().indexOf('"', start);
        return end < 0 ? "" : credential.getCredentialData().substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private RealmModel realm() {
        return session.getContext().getRealm();
    }

    private static String credentialType(String requested) {
        if (PASSKEY_TYPE.equals(requested)) {
            return PASSKEY_TYPE;
        }
        if (PIN_TYPE.equals(requested)) {
            return PIN_TYPE;
        }
        return BIOMETRIC_TYPE;
    }

    private static String labelPrefix(String type) {
        if (PIN_TYPE.equals(type)) {
            return "pin";
        }
        return PASSKEY_TYPE.equals(type) ? "passkey" : "biometric";
    }

    static boolean verifyPin(UserModel user, String pin) {
        CredentialModel credential = findPinCredential(user);
        if (credential == null || blank(pin)) {
            return false;
        }
        String salt = value(credential.getCredentialData(), "salt");
        String hash = value(credential.getSecretData(), "hash");
        return !blank(salt) && !blank(hash) && hash.equals(hashPin(pin, salt));
    }

    private static void upsertPin(UserModel user, String pin) {
        CredentialModel existing = findPinCredential(user);
        if (existing != null) {
            user.credentialManager().removeStoredCredentialById(existing.getId());
        }
        String salt = token(16);
        CredentialModel credential = new CredentialModel();
        credential.setType(PIN_TYPE);
        credential.setUserLabel("pin");
        credential.setCreatedDate(System.currentTimeMillis());
        credential.setCredentialData("{\"salt\":\"" + salt + "\",\"algorithm\":\"PBKDF2WithHmacSHA256\",\"iterations\":120000}");
        credential.setSecretData("{\"hash\":\"" + hashPin(pin, salt) + "\"}");
        user.credentialManager().createStoredCredential(credential);
    }

    private static CredentialModel findPinCredential(UserModel user) {
        if (user == null) {
            return null;
        }
        return user.credentialManager().getStoredCredentialsByTypeStream(PIN_TYPE)
                .filter(it -> "pin".equals(it.getUserLabel()))
                .findFirst()
                .orElse(null);
    }

    private static String hashPin(String pin, String salt) {
        try {
            KeySpec spec = new PBEKeySpec(pin.toCharArray(), Base64.getDecoder().decode(salt), 120000, 256);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "";
        }
    }

    private static String value(String json, String name) {
        if (json == null) {
            return "";
        }
        String marker = "\"" + name + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        int end = json.indexOf('"', start);
        return end < 0 ? "" : json.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String token(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getEncoder().encodeToString(value);
    }

    private boolean validSecret() {
        String expected = env("TSB_AUTH_BIOMETRIC_SECRET", "local-biometric-grant-secret");
        String actual = session.getContext().getRequestHeaders().getHeaderString("X-Keycloak-Biometric-Secret");
        return expected.equals(actual);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
