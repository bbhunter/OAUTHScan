package burp;


/*
# OAUTHScan
#
# OAUTHScan is a Burp Suite Extension written in Java with the aim to provide some automatic security checks, 
# which could be useful during penetration testing on applications implementing OAUTHv2 and OpenID standards.
#
# The plugin looks for various OAUTHv2/OpenID vulnerabilities and common misconfigurations (based on 
# official specifications of both frameworks).
#
# Copyright (C) 2022 Maurizio Siddu
#
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>
*/




import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;




public class BurpExtender implements IBurpExtender, IScannerCheck, IScannerInsertionPointProvider, IExtensionStateListener {
	private IBurpExtenderCallbacks callbacks;
	private IExtensionHelpers helpers;

	private static PrintWriter stdout;
	private static PrintWriter stderr;

    public final String PLUGIN_NAME    = "OAUTHScan";
	public final String PLUGIN_VERSION = "1.0";
	public final String AUTHOR  = "Maurizio Siddu";


    // List of system already tested for wellknown urls
    private static List<String> alreadyChecked = new ArrayList<>();

    // Range of value to generate random strings
    static final String RANGE = "0123456789abcdefghijklmnopqrstuvwxyz";
    static SecureRandom rnd = new SecureRandom();

    private static final List<String> INJ_REDIR = new ArrayList<>();
    static {
        INJ_REDIR.add("/../../../../../notexist");
        INJ_REDIR.add("%2f%2e%2e%2f%2e%2e%2f%2e%2e%2f%2e%2e%2fnotexist");
        INJ_REDIR.add("/..;/..;/..;/../testOauth");
        INJ_REDIR.add("https://SUBDOMAINCHANGEME.burpcollaborator.net/");
        INJ_REDIR.add("@SUBDOMAINCHANGEME.burpcollaborator.net/");
        INJ_REDIR.add("https://SUBDOMAINCHANGEME.burpcollaborator.net#");
        INJ_REDIR.add(":password@https://SUBDOMAINCHANGEME.burpcollaborator.net#");
        INJ_REDIR.add(".SUBDOMAINCHANGEME.burpcollaborator.net/");
        INJ_REDIR.add("https://localhost.burpcollaborator.net/");
        INJ_REDIR.add("&redirect_uri=https://SUBDOMAINCHANGEME.burpcollaborator.net/");
        INJ_REDIR.add("https://127.0.0.1/");
        INJ_REDIR.add("http://127.0.0.1/");
        INJ_REDIR.add("http://localhost/");
        INJ_REDIR.add("http://2130706433");
        INJ_REDIR.add("HOST_HEADER");
        INJ_REDIR.add("/../../../../../notexist&response_mode=fragment"); // payload only for OpenID cases 
    }

    private static final List<String> INJ_SCOPE = new ArrayList<>();
    static {
        INJ_SCOPE.add("notexist");
        INJ_SCOPE.add("admin");
        INJ_SCOPE.add("premium");
        INJ_SCOPE.add("profile%20email%20address%20phone");
        INJ_SCOPE.add("write%20read");
        INJ_SCOPE.add("private");
        INJ_SCOPE.add("test");
        INJ_SCOPE.add("email");
        INJ_SCOPE.add("profile");
        INJ_SCOPE.add("offline_access");
        INJ_SCOPE.add("address");
        INJ_SCOPE.add("phone");
        INJ_SCOPE.add("okta.apps.manage");
        INJ_SCOPE.add("okta.apps.read");
        INJ_SCOPE.add("okta.authorizationServers.manage");
        INJ_SCOPE.add("okta.authorizationServers.read");
        INJ_SCOPE.add("okta.clients.manage");
        INJ_SCOPE.add("okta.clients.read");
        INJ_SCOPE.add("okta.clients.register");
        INJ_SCOPE.add("okta.devices.manage");
        INJ_SCOPE.add("okta.devices.read");
        INJ_SCOPE.add("okta.domains.manage");
        INJ_SCOPE.add("okta.domains.read");
        INJ_SCOPE.add("okta.eventHooks.manage");
        INJ_SCOPE.add("okta.eventHooks.read");
        INJ_SCOPE.add("okta.factors.manage");
        INJ_SCOPE.add("okta.factors.read");
        INJ_SCOPE.add("okta.groups.manage");
        INJ_SCOPE.add("okta.groups.read");
        INJ_SCOPE.add("okta.idps.manage");
        INJ_SCOPE.add("okta.idps.read");
        INJ_SCOPE.add("okta.inlineHooks.manage");
        INJ_SCOPE.add("okta.inlineHooks.read");
        INJ_SCOPE.add("okta.linkedObjects.manage");
        INJ_SCOPE.add("okta.linkedObjects.read");
        INJ_SCOPE.add("okta.logs.read");
        INJ_SCOPE.add("okta.policies.read");
        INJ_SCOPE.add("okta.profileMappings.manage");
        INJ_SCOPE.add("okta.profileMappings.read");
        INJ_SCOPE.add("okta.roles.manage");
        INJ_SCOPE.add("okta.roles.read");
        INJ_SCOPE.add("okta.schemas.manage");
        INJ_SCOPE.add("okta.schemas.read");
        INJ_SCOPE.add("okta.sessions.manage");
        INJ_SCOPE.add("okta.sessions.read");
        INJ_SCOPE.add("okta.templates.manage");
        INJ_SCOPE.add("okta.templates.read");
        INJ_SCOPE.add("okta.trustedOrigins.manage");
        INJ_SCOPE.add("okta.trustedOrigins.read");
        INJ_SCOPE.add("okta.users.manage");
        INJ_SCOPE.add("okta.users.read");
        INJ_SCOPE.add("okta.users.manage.self");
        INJ_SCOPE.add("okta.users.read.self");
        INJ_SCOPE.add("okta.userTypes.manage");
        INJ_SCOPE.add("okta.userTypes.read");
        INJ_SCOPE.add("read_repository");
        INJ_SCOPE.add("write_repository");
        INJ_SCOPE.add("sudo");
        INJ_SCOPE.add("api");
        INJ_SCOPE.add("profile:user_id");
        INJ_SCOPE.add("postal_code");
        INJ_SCOPE.add("cdp_query_api");
        INJ_SCOPE.add("pardot_api");
        INJ_SCOPE.add("cdp_profile_api");
        INJ_SCOPE.add("chatter_api");
        INJ_SCOPE.add("cdp_ingest_api");
        INJ_SCOPE.add("eclair_api");
        INJ_SCOPE.add("wave_api");
        INJ_SCOPE.add("custom_permissions");
        INJ_SCOPE.add("lightning");
        INJ_SCOPE.add("content");
        INJ_SCOPE.add("full");
        INJ_SCOPE.add("refresh_token");
        INJ_SCOPE.add("visualforce");
        INJ_SCOPE.add("web");
    }

    private static final List<String> WELL_KNOWN = new ArrayList<>();
    static {
        WELL_KNOWN.add("/.well-known/openid-configuration");
        WELL_KNOWN.add("/.well-known/oauth-authorization-server");
        WELL_KNOWN.add("/.well-known/webfinger");
        WELL_KNOWN.add("/openam/.well-known/webfinger");
        WELL_KNOWN.add("/.well-known/host-meta");
        WELL_KNOWN.add("/.well-known/oidcdiscovery");
        WELL_KNOWN.add("/.well-known/webfinger?resource=ORIGINCHANGEME/anonymous&rel=http://openid.net/specs/connect/1.0/issuer");
        WELL_KNOWN.add("/.well-known/webfinger?resource=acct:USERCHANGEME@URLCHANGEME&rel=http://openid.net/specs/connect/1.0/issuer");
    }

    private List<String> GOTOPENIDTOKENS = new ArrayList<>();
    private Map<String, List<String>> GOTTOKENS = new HashMap<String, List<String>>();
    private Map<String, List<String>> GOTCODES = new HashMap<String, List<String>>();
    private Map<String, List<String>> GOTSTATES = new HashMap<String, List<String>>();

    private static final List<String> SECRETTOKENS = new ArrayList<>();
    static {
        SECRETTOKENS.add("Access_Token");
        SECRETTOKENS.add("Access-Token");
        SECRETTOKENS.add("AccessToken");
        SECRETTOKENS.add("Refresh_Token");
        SECRETTOKENS.add("Refresh-Token");
        SECRETTOKENS.add("RefreshToken");
        SECRETTOKENS.add("Token");
        SECRETTOKENS.add("SSO_Auth");
        SECRETTOKENS.add("SSO-Auth");
        SECRETTOKENS.add("SSOAuth");
    }

    private static final List<String> SECRETCODES = new ArrayList<>();
    static {
        SECRETCODES.add("Code");
        SECRETCODES.add("AuthCode");
        SECRETCODES.add("Auth_Code");
        SECRETCODES.add("Auth-Code");
        SECRETCODES.add("AuthenticationCode");
        SECRETCODES.add("Authentication_Code");
        SECRETCODES.add("Authentication-Code");
        SECRETCODES.add("oauth_token");
        SECRETCODES.add("oauth-token");
        SECRETCODES.add("oauthtoken");
        
    }

    private static final List<String> OPENIDTOKENS = new ArrayList<>();
    static {
        OPENIDTOKENS.add("Id_Token");
        OPENIDTOKENS.add("Id-Token");
        OPENIDTOKENS.add("IdToken");
    }

    private static final List<String> EXPIRATIONS = new ArrayList<>();
    static {
        OPENIDTOKENS.add("Expires_In");
        OPENIDTOKENS.add("Expires-In");
        OPENIDTOKENS.add("ExpiresIn");
        OPENIDTOKENS.add("Expires");
        OPENIDTOKENS.add("Expiration");
    }

    // implementing IBurpExtender
    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks)
    {
        // keep a reference to our callbacks object
        this.callbacks = callbacks;
        // obtain an extension helpers object
        helpers = callbacks.getHelpers();
        BurpExtender.stdout = new PrintWriter(callbacks.getStdout(), true);
        BurpExtender.stderr = new PrintWriter(callbacks.getStderr(), true);

        // Set extension name
        callbacks.setExtensionName(PLUGIN_NAME);
        callbacks.registerScannerInsertionPointProvider(this);
        callbacks.registerScannerCheck(this);
        stdout.println("[+] OAUTHscan Plugin Loaded Successfully");
    }


    
    public static Map<String, String> getQueryMap(String query) {  
        // Extract the params from URL query
        Map<String, String> qmap = new HashMap<String, String>();
        if (query == null) {
            return null;
        }
        String[] qparams = query.split("&");
        for (String qparam : qparams) { 
            if (qparam.split("=").length > 1) {
                String name = qparam.split("=")[0];
                String value = qparam.split("=")[1];  
                qmap.put(name, value); 
            }
        }  
        return qmap;  
    }



    
    String randomString(int len) {
        // Random string generator
        StringBuilder sbuilt = new StringBuilder(len);
        for(int i = 0; i < len; i++)
            sbuilt.append(RANGE.charAt(rnd.nextInt(RANGE.length())));
        return sbuilt.toString();
    }



    public String getHttpHeaderValueFromList(List<String> listOfHeaders, String headerName) {
        // Extract heder value if present in the specified list of strings
        if (listOfHeaders != null) {
            for(String item: listOfHeaders) {
                if (item.toLowerCase().contains(headerName.toLowerCase())) {
                    String[] headerItems = item.split(":", 2);
                    if (headerItems.length >= 1) {
                        return item.split(":", 2)[1];
                    }
                }
            }
        }
        return null;
    }



    public String getUrlOriginString(String urlstring) {
        // Retrieve origin value from url-string
        String origin = "";
        if (urlstring.contains("%")) {
            // If url is encoded then first decode it 
            helpers.urlDecode(urlstring);
        }
        if (!urlstring.isEmpty() & urlstring!=null) {
            Pattern pattern = Pattern.compile("(https?://)([^:^/]*)(:\\d*)?(.*)?");
            Matcher matcher = pattern.matcher(urlstring);
            if (matcher.find()) {
                if (matcher.group(3)==null || matcher.group(3).isEmpty() || matcher.group(3).equals("80") || matcher.group(3).equals("443")) {
                    origin = matcher.group(1)+matcher.group(2);
                } else {
                    origin = matcher.group(1)+matcher.group(2)+matcher.group(3);
                }
            }
        }
        return origin;
    }



    // Helper method to search a response for occurrences of a literal match string
	// and return a list of start/end offsets
	private List<int[]> getMatches(byte[] response, byte[] match) {
		List<int[]> matches = new ArrayList<int[]>();
		int start = 0;
		while (start < response.length)
		{
			start = helpers.indexOf(response, match, false, start, response.length);
			if (start == -1) break;
			matches.add(new int[] { start, start + match.length });
			start += match.length;
		}
		return matches;
	}



    // Method to search specified patterns on HTTP request and responses
    public List<String> getMatchingParams(String paramName, String toSearch, String data, String mimeType) {
        List<String> matches = new ArrayList<String>();
        Pattern pattern = null;
        String data_lower;
        if (data!=null) {
            // Case insensitive search
            paramName = paramName.toLowerCase();
            toSearch = toSearch.toLowerCase();
            data_lower = data.toLowerCase();
            if (data_lower.contains(toSearch)) {
                if (mimeType == null) {
                    // Parameter in response without a Content-Type
                    pattern = Pattern.compile("[&\\?]?" + paramName + "=([A-Za-z0-9\\-_\\.~\\+/]+)[&]?");
                } else if (mimeType.toLowerCase().contains("json")) {
                    // Parameter in Json body
                    pattern = Pattern.compile("['\"]{1}" + paramName + "['\"]{1}[\\s]*:[\\s]*['\"]{1}([A-Za-z0-9\\-_\\.~\\+/]+)['\"]{1}");
                } else if (mimeType.contains("xml") ) {
                    // Parameter in xml body
                    pattern = Pattern.compile("<" + paramName + ">[\\s\\n]<([A-Za-z0-9\\-_\\.~\\+/]+)>");
                } else if (mimeType == "header" || (data.contains("Location: ") & data.contains("302 Found"))) {
                    // Parmeter in Location header Url
                    pattern = Pattern.compile("[&\\?]?" + paramName + "=([A-Za-z0-9\\-_\\.~\\+/]+)[&]?");
                } else if (mimeType == "link") {
                    // Parameter in url of HTML link tag like "<a href=" or "<meta http-equiv=refresh content='3;url="
                    pattern = Pattern.compile("[&\\?]?" + paramName + "=([A-Za-z0-9\\-_\\.~\\+/]+)[&]?");
                } else {
                    // Parameter in text/html body
                    if (data.contains("location.href") || data.contains("location.replace") || data.contains("location.assign")) {
                        // If parameter is in javascript content
                        pattern = Pattern.compile("[&\\?]?" + paramName + "=([A-Za-z0-9\\-_\\.~\\+/]+)[&]?");    
                    } else {
                        // If parameter is within an HTML page                 
                        pattern = Pattern.compile("['\"]{1}" + paramName + "['\"]{1}[\\s]*value=['\"]{1}([A-Za-z0-9\\-_\\.~\\+/]+)['\"]{1}");
                    }   
                }
                if (pattern == null) {
                    return matches;
                }
                Matcher matcher = pattern.matcher(data_lower);
                // Get all matching strings in body
                while(matcher.find()) {
                    int start = matcher.start(1);
                    int end = matcher.end(1);
                    matches.add(data.substring(start, end));
                }
            } 
        }
        // Finally remove duplicate values
        matches = new ArrayList<>(new HashSet<>(matches));
        return matches;
    }





    // Passive Scan section ///////////////////////////////

    @Override
    public List<IScanIssue> doPassiveScan(IHttpRequestResponse baseRequestResponse) {
        String dateToken = "";
        String dateCode = "";
        long currentTimeStampMillis = Instant.now().toEpochMilli();
        List<IScanIssue> issues = new ArrayList<>();

        // Getting request an response data
        byte[] rawRequest = baseRequestResponse.getRequest();
        byte[] rawResponse = baseRequestResponse.getResponse();
        String requestString = helpers.bytesToString(rawRequest);
        String responseString = helpers.bytesToString(rawResponse);
        IRequestInfo reqInfo = helpers.analyzeRequest(baseRequestResponse);
        IResponseInfo respInfo = helpers.analyzeResponse(rawResponse);

        // Getting the Request URL query parameters 
        Map<String, String> reqQueryParam = new HashMap<String, String>();
        if (reqInfo.getUrl().getQuery() != null) {
            reqQueryParam = getQueryMap(reqInfo.getUrl().getQuery());
        }
        //Map<String, String> reqQueryParam = getQueryMap(reqInfo.getUrl().getQuery());
        String reqQueryString = reqInfo.getUrl().toString();
        String respType = "";
        String redirUri = "";

        // Getting the Request Params and Headers
        List<IParameter> reqParam = reqInfo.getParameters();
        List<String> reqHeaders = reqInfo.getHeaders();
        //String reqBodyString = new String(Arrays.copyOfRange(rawRequest, reqInfo.getBodyOffset(), rawRequest.length));
        
        // Getting the Response Headers and Body 
        List<String> respHeaders = respInfo.getHeaders();
        String respBody = "";
        
        // Check the presence of body in HTTP response based on RFC 7230 https://tools.ietf.org/html/rfc7230#section-3.3
        if ( (getHttpHeaderValueFromList(respHeaders, "Transfer-Encoding")!=null || getHttpHeaderValueFromList(respHeaders, "Content-Length")!=null) && (!reqInfo.getMethod().toLowerCase().contains("head")) ) {
            respBody = responseString.substring(respInfo.getBodyOffset()).trim();
        }

        // Searching for HTTP responses releasing secret tokens in body or Location header
        if (!respBody.isEmpty() || respInfo.getStatusCode() ==302) {
            // Checking for Duplicate Token value issues on OAUTHv2 and OpenID
            if (! GOTTOKENS.isEmpty()) {
                String respDate = getHttpHeaderValueFromList(respHeaders, "Date");
                if (getHttpHeaderValueFromList(respHeaders, "Date") == null) {
                    // This is needed to avoid null values on respDate
                    respDate = Long.toString(currentTimeStampMillis);
                }
                // Start searching if last issued secret token is a duplicated of already received tokens
                for (Map.Entry<String,List<String>> entry : GOTTOKENS.entrySet()) {
                    List<String> tokenList = entry.getValue();
                    String tokenDate = entry.getKey();
                    for (String tokenValue: tokenList) {
                        if (responseString.toLowerCase().contains(tokenValue) & (! tokenDate.equals(respDate))) {
                            // This OAUTHv2/OpenID Flow response contains an already released Secret Token
                            List<int[]> matches = getMatches(responseString.getBytes(), tokenValue.getBytes());
                            issues.add(
                                new CustomScanIssue(
                                    baseRequestResponse.getHttpService(),
                                    helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                    new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, matches) },
                                    "OAUTHv2/OpenID Duplicate Secret Token Detected",
                                    "The Authorization Server releases duplicate secret token (Access or Refersh Token) values "
                                    +"after successful OAUTHv2/OpenID login procedure.\n<br> For security reasons the OAUTHv2/OpenID "
                                    +"specifications require that secret token must be unique for each user's session.\n<br>"
                                    +"The response contains the following already released secret token value <b>"+tokenValue+"</b>\n<br>"
                                    +"Note: this issue should be <b>confirmed manually</b> by searching the duplicated secret token "
                                    +"values in the burp-proxy history.",
                                    "Medium",
                                    "Firm"
                                )
                            );
                        }
                    }
                }
            }

            // Enumerate OAUTHv2/OpenID secret tokens returned by HTTP responses
            dateToken = getHttpHeaderValueFromList(respHeaders, "Date");
            if (getHttpHeaderValueFromList(respHeaders, "Date")==null) {
                // This is needed to avoid null values on GOTTOKENS
                dateToken = Long.toString(currentTimeStampMillis);
            }
            List<String> foundTokens = new ArrayList<>();
            for (String pName : SECRETTOKENS) {
                if (! GOTTOKENS.containsKey(dateToken)) {
                    foundTokens.addAll(getMatchingParams(pName, pName, respBody, getHttpHeaderValueFromList(respHeaders, "Content-Type")));
                    foundTokens.addAll(getMatchingParams(pName, pName, getHttpHeaderValueFromList(respHeaders, "Location"), "header"));
                    foundTokens.addAll(getMatchingParams(pName, pName, respBody, "link"));
                    // Remove duplicate tokens found in same request
                    foundTokens = new ArrayList<>(new HashSet<>(foundTokens));
                    GOTTOKENS.put(dateToken, foundTokens);
                }
            }


            // Checking for Lifetime issues on released Secret Tokens (Access and Refresh Tokens)
            for (String pName : SECRETTOKENS) {
                for (String expName : EXPIRATIONS) {
                    List<String> expirList = getMatchingParams(expName, pName, respBody, getHttpHeaderValueFromList(respHeaders, "Content-Type"));
                    if (expirList.isEmpty()) {
                        // Check if a secret token is issued without expiration time
                        if (!getMatchingParams(pName, pName, respBody, getHttpHeaderValueFromList(respHeaders, "Content-Type")).isEmpty()) {
                            issues.add(
                                new CustomScanIssue(
                                    baseRequestResponse.getHttpService(),
                                    helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                    new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                                    "OAUTHv2/OpenID Misconfiguration - Secret Tokens Without Expiration Parameter",
                                    "It seems that after successuful login the Authorization Server releases a OAUTHv2/OpenID secret token which never expires.\n<br>"
                                    +"More specifically, the secret token <b>"+pName+"</b> returned in response does not has associated an expiration <code>expires_in</code> parameter.\n<br> "
                                    +"This issue could be a false positive, then it is suggested to double-check it manually.\n<br> "
                                    +"If the Authorization Server releases secret tokens which never expire, it exposes the OAUTHv2/OpenID platform "
                                    +"to various security risks of in case of accidental leakage of a secret token.\n<br>"
                                    +"If possible it is advisable to force expiration for Access Token after 1 hour, and for Refresh Token after 2 hours\n<br>"
                                    +"<br>References:<br>"
                                    +"<a href=\"https://www.rfc-editor.org/rfc/rfc6819#page-54\">https://www.rfc-editor.org/rfc/rfc6819#page-54</a>",
                                    "High",
                                    "Firm"
                                )
                            );
                        }
                    // Check if secret token lifetime is excessive
                    } else {
                        for (String expirTime : expirList) {
                            // Considering excessive an expiration greater than 2 hours
                            if (Integer.parseInt(expirTime) > 7200) {
                                issues.add(
                                    new CustomScanIssue(
                                        baseRequestResponse.getHttpService(),
                                        helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                        new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                                        "OAUTHv2/OpenID Misconfiguration - Excessive Lifetime for Secret Tokens",
                                        "Detected an excessive lifetime for the OAUTHv2/OpenID secret tokens released after a successful login.\n<br> "
                                        +"More specifically the issued secret token <b>"+pName+"</b> expires in <b>"+expirTime+"</b> seconds.\n<br> "
                                        +"If possible, it is advisable to set an Access Token expiration time of an 1 hour, and set a Refresh Token expiration time of 2 hours\n<br>"
                                        +"<br>References:<br>"
                                        +"<a href=\"https://www.rfc-editor.org/rfc/rfc6819#page-54\">https://www.rfc-editor.org/rfc/rfc6819#page-54</a>",
                                        "Medium",
                                        "Firm"
                                    )
                                );
                            }
                        }
                    }
                }
            }
        }



        // Retrieve some OAUTHv2 request parameters
        IParameter scopeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "scope");
        IParameter resptypeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "response_type");
        IParameter redirParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "redirect_uri");
        IParameter clientidParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "client_id");
        IParameter stateParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "state");
        IParameter grantParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "grant_type");
        IParameter challengeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "code_challenge");
        IParameter challengemethodParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "code_challenge_method");
        IParameter requesturiParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "request_uri");
        IParameter nonceParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "nonce");
        IParameter respmodeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "response_mode");

        // First check if request belongs to a OpenID Flow
        Boolean isOpenID = false;
        Boolean foundRefresh = false;
        if (scopeParameter!=null) {
            if (scopeParameter.getValue().contains("openid")) {
                isOpenID = true;
            }
        } else if (resptypeParameter!=null) {
            if (helpers.urlDecode(resptypeParameter.getValue()).contains("id_token") || helpers.urlDecode(resptypeParameter.getValue()).equals("code token")) {
                isOpenID = true;
            }
        } 

        // Starting specific passive checks on OpenID requests
        if (isOpenID) {  
            if (!respBody.isEmpty() || respInfo.getStatusCode()==302) {
                // Enumerate OpenID id_tokens returned by HTTP responses
                List<String> foundIdTokens = new ArrayList<>();
                for (String pName : OPENIDTOKENS) {
                    foundIdTokens.addAll(getMatchingParams(pName, pName, respBody, getHttpHeaderValueFromList(respHeaders, "Content-Type")));
                    foundIdTokens.addAll(getMatchingParams(pName, pName, getHttpHeaderValueFromList(respHeaders, "Location"), "header"));
                    foundIdTokens.addAll(getMatchingParams(pName, pName, respBody, "link"));
                    // Remove duplicate id_tokens found in same request
                    foundIdTokens = new ArrayList<>(new HashSet<>(foundIdTokens));
                    GOTOPENIDTOKENS.addAll(foundIdTokens);
                }
            }
        
            // Check for weak OpenID nonce values (i.e. insufficient length, only alphabetic, only numeric, etc.)
            if (nonceParameter!=null) {
                String nonceValue = helpers.urlDecode(nonceParameter.getValue());
                if ( (nonceValue.length() < 5) || ( (nonceValue.length() < 7) & ((nonceValue.matches("[a-zA-Z]+")) || (nonceValue.matches("[\\-\\+]?[0-9]+")))) ) {
                    List<int[]> requestHighlights = new ArrayList<>(1);
                    int[] nonceOffset = new int[2];
                    int nonceStart = requestString.indexOf(nonceValue);
                    nonceOffset[0] = nonceStart;
                    nonceOffset[1] = nonceStart+nonceValue.length();
                    requestHighlights.add(nonceOffset);
                    issues.add(
                        new CustomScanIssue(
                            baseRequestResponse.getHttpService(),
                            helpers.analyzeRequest(baseRequestResponse).getUrl(),
                            new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, requestHighlights, null) },
                            "OpenID Weak Nonce Parameter",
                            "The OpenID Flow presents a security misconfiguration, the Authorization Server accepts weak values "
                            +"of the <code>nonce</code> parameter received during OpenID login procedure.\n<br> "
                            +"In details the OpenID Flow request contains a <code>nonce</code> value of <b>"+nonceValue+"</b>.\n<br>"
                            +"Based on OpenID specifications the <code>nonce</code> parameter is used to associate a Client session "
                            +"with an ID Token, and to mitigate replay attacks. For these reasons it should be unpredictable and unique "
                            +"per client session.\n<br>Since the <code>nonce</code> value is guessable (insufficient entropy) "
                            +"then the attack surface of the OpenID service increases.\n<br>"
                            +"<br>References:<br>"
                            +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html#NonceNotes\">https://openid.net/specs/openid-connect-core-1_0.html#NonceNotes</a>",
                            "Low",
                            "Firm"
                        )
                    );
                }
            }

            // Check for weak OpenID state values (i.e. insufficient length, only alphabetic, only numeric, etc.)
            if (stateParameter!=null) {
                String stateValue = stateParameter.getValue();
                if ( (stateValue.length() < 5) || ( (stateValue.length() < 7) & ((stateValue.matches("[a-zA-Z]+")) || (stateValue.matches("[0-9]+")))) ) {
                    List<int[]> requestHighlights = new ArrayList<>(1);
                    int[] nonceOffset = new int[2];
                    int nonceStart = requestString.indexOf(stateValue);
                    nonceOffset[0] = nonceStart;
                    nonceOffset[1] = nonceStart+stateValue.length();
                    requestHighlights.add(nonceOffset);
                    issues.add(
                        new CustomScanIssue(
                            baseRequestResponse.getHttpService(),
                            helpers.analyzeRequest(baseRequestResponse).getUrl(),
                            new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, requestHighlights, null) },
                            "OpenID Weak State Parameter",
                            "The OpenID Flow presents a security misconfiguration because is using weak values for"
                            +"the <code>state</code> parameter during OpenID login procedure.\n<br> "
                            +"In details the OpenID Flow request contains the following <code>state</code> parameter weak value <b>"+stateValue+"</b>.\n<br>"
                            +"Based on OpenID specifications the <code>state</code> parameter should be used to maintain state between "
                            +"the request and the callback, and to mitigate CSRF attacks. For these reasons its value should be unpredictable and unique "
                            +"for usr's session.\n<br>When the <code>state</code> value is guessable (insufficient entropy) "
                            +"then the attack surface of the OpenID service increases.\n<br>"
                            +"<br>References:<br>"
                            +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html\">https://openid.net/specs/openid-connect-core-1_0.html</a>",
                            "Low",
                            "Firm"
                        )
                    );
                }
            }



            // Checking for OpenID Flows with 'request_uri' parameter
            if (requesturiParameter!=null) {
                issues.add(
                    new CustomScanIssue(
                        baseRequestResponse.getHttpService(),
                        helpers.analyzeRequest(baseRequestResponse).getUrl(),
                        new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                        "OpenID Flow with Request_Uri Parameter Detected",
                        "The OpenID Flow uses the parameter <code>request_uri</code> to "
                        +"enable the retrieving of client's Request-Object via a URI reference to it.\n<br>"
                        +"Based on OpenID specifications the value of the <code>request_uri</code> parameter "
                        +"is set to an URI pointing to a server hosting a JWT which contains the client's parameter values. "
                        +"In this way the OpenID Provider can fetch the provided URI and retrieve the Request-Object "
                        +"by parsing the JWT contents.\n<br>"
                        +"For security reasons the URI value of <code>request_uri</code> parameter should be carefully validated "
                        +"at server-side, otherwise a threat agent could be able to lead the OpenID Provider to interact with "
                        +"an arbitrary server under is control and then potentially exploit SSRF vulnerabilities.\n<br>"
                        +"As mitigation the OpenID Provider should define a whitelist of allowed URI values (pre-registered "
                        +"during the client registration process) for the <code>request_uri</code> parameter.\n<br>"
                        +"<br>References:<br>"
                        +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.6.2\">https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.6.2</a>"
                        +"<a href=\"https://portswigger.net/research/hidden-oauth-attack-vectors\">https://portswigger.net/research/hidden-oauth-attack-vectors</a>",
                        "Information",
                        "Certain"
                    )
                );
            }

            // Checks for all OpenID Flows login requests
            if ( ((reqQueryParam!=null & reqQueryParam.containsKey("client_id") & reqQueryParam.containsKey("response_type")) || 
            ( reqParam!=null & (clientidParameter != null) & (resptypeParameter!=null))) ) {
                stdout.println("[+] Passive Scan: OpenID Flow detected");
                if (reqQueryParam.containsKey("redirect_uri") & reqQueryParam.containsKey("response_type")) {
                    respType = reqQueryParam.get("response_type");
                    redirUri = reqQueryParam.get("redirect_uri");
                } else if ((redirParameter != null) & (resptypeParameter!=null)) {
                    respType = resptypeParameter.getValue();
                    redirUri = redirParameter.getValue();
                }
                
                // Checking for OpenID Implicit Flow
                if (respType.equals("token") || respType.equals("id_token") || helpers.urlDecode(respType).equals("id_token token")){
                    // Found OpenID Implicit Flow
                    issues.add(
                        new CustomScanIssue(
                            baseRequestResponse.getHttpService(),
                            helpers.analyzeRequest(baseRequestResponse).getUrl(),
                            new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                            "OpenID Implicit Flow Detected",
                            "This is a login request of OpenID Implicit Flow.\n<br>"
                            +"Note: OpenID Implicit Flow should be avoided in Mobile application contexts because considered insecure.",
                            "Information",
                            "Certain"
                        )
                    );
                    // Checking for OpenID Implicit Flow misconfiguration (missing nonce)
                    if (nonceParameter==null) {
                        issues.add(
                            new CustomScanIssue(
                                baseRequestResponse.getHttpService(),
                                helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                                "OpenID Implicit Flow without Nonce Parameter",
                                "The OpenID Implicit Flow is improperly implemented because the "
                                +"mandatory <code>nonce</code> is missing.\n<br>This parameter is randomic and unique per "
                                +"client session in order to provide a security mitigation against replay attacks, its absence "
                                +"increases the attack surface of the OpenID service.\n<br>"
                                +"Note: conthe Implicit Flow should be avoided in Mobile application contexts because is inerently insecure.\n<br>"
                                +"<br>References:<br>"
                                +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html#ImplicitAuthRequest\">https://openid.net/specs/openid-connect-core-1_0.html#ImplicitAuthRequest</a><br>"
                                +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html#NonceNotes\">https://openid.net/specs/openid-connect-core-1_0.html#NonceNotes</a>",
                                "Medium",
                                "Certain"
                            )
                        );
                    }

                    // Checking for OpenID Implicit Flow Deprecated Implementation with access token in URL
                    if (respType.equals("token") || helpers.urlDecode(respType).equals("id_token token")) {
                        // If response_mode is set to form_post then the Implicit Flow is yet acceptable
                        if ( respmodeParameter==null || (!respmodeParameter.getValue().equals("form_post")) ) {
                            // Found dangerous implementation of OpenID Implicit Flow which exposes access tokens in URL
                            issues.add(
                                new CustomScanIssue(
                                    baseRequestResponse.getHttpService(),
                                    helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                    new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                                    "OpenID Implicit Flow Insecure Implementation Detected",
                                    "This OpenID Implicit Flow implementation is inerently insecure because enables the transmission of "
                                    +"the access token on the URL of HTTP GET requests.\n<br>.This behaviour is deprecated by OpenID specifications "
                                    +"because exposes to leakages (i.e. via cache, traffic sniffing, etc.) and replay attacks of access tokens.\n<br>"
                                    +"If the use of OpenID Implicit Flow is needed then is suggested to use the <code>request_mode</code> set to "
                                    +"<b>form_post</b> which force to send access tokens in the body of HTTP POST requests, or to"
                                    +"adopt the OpenID Implicit Flow which uses only the ID_Token (not exposing access tokens) "
                                    +"by setting <code>response_type</code> parameter to <b>id_token</b>.\n<br>"
                                    +"Note: the use of Implicit Flow is also considered insecure in Mobile application contexts.\n<br>"
                                    +"<br>References:<br>"
                                    +"<a href=\"https://openid.net/specs/oauth-v2-form-post-response-mode-1_0.html\">https://openid.net/specs/oauth-v2-form-post-response-mode-1_0.html</a>",
                                    "Medium",
                                    "Certain"
                                )
                            );
                        } else {
                            
                        }
                    }


                // Checking for OpenID Hybrid Flow authorization requests
                } else if ( (helpers.urlDecode(respType).equals("code id_token") || helpers.urlDecode(respType).equals("code token") || helpers.urlDecode(respType).equals("code id_token token")) ) {
                    // Checking for Duplicate Code value issues on OpenID Hybrid Flow
                    if (! GOTCODES.isEmpty()) {
                        String respDate = getHttpHeaderValueFromList(respHeaders, "Date");
                        if (getHttpHeaderValueFromList(respHeaders, "Date") == null) {
                            // This is needed to avoid null values on respDate
                            respDate = Long.toString(currentTimeStampMillis);
                        }
                        // Start searching of authorization code duplicates
                        for (Map.Entry<String,List<String>> entry : GOTCODES.entrySet()) {
                            List<String> codeList = entry.getValue();
                            String codeDate = entry.getKey();
                            for (String codeValue: codeList) {
                                if (responseString.toLowerCase().contains(codeValue) & (! codeDate.equals(respDate))) {
                                    // This Hybrid Flow response contains an already released Code
                                    List<int[]> matches = getMatches(responseString.getBytes(), codeValue.getBytes());
                                    issues.add(
                                        new CustomScanIssue(
                                            baseRequestResponse.getHttpService(),
                                            helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                            new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, matches) },
                                            "OpenID Duplicate Authorization Code Detected",
                                            "The Authorization Server releases duplicate values for <code>code</code> parameter "
                                            +"during Hybrid Flow login procedure.\n<br>For security reasons the OpenID "
                                            +"specifications recommend that authorization code must be unique for each user's session.\n<br>"
                                            +"The response contains the following already released <code>code</code> value <b>"+codeValue+"</b>\n<br>"
                                            +"Note: this issue should be <b>confirmed manually</b> by searching the duplicated authorization code "
                                            +"values in the burp-proxy history.\n<br>"
                                            +"<br>References:<br>"
                                            +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html#TokenRequestValidation\">https://openid.net/specs/openid-connect-core-1_0.html#TokenRequestValidation</a>",
                                            "Medium",
                                            "Firm"
                                        )
                                    );
                                }
                            }
                        }
                    }

                    // Retrieving codes from OpenID Hybrid Flow responses body or Location header
                    if (!respBody.isEmpty() || respInfo.getStatusCode()==302) {
                        // Enumerate OpenID authorization codes returned by HTTP responses
                        dateCode = getHttpHeaderValueFromList(respHeaders, "Date");
                        if (getHttpHeaderValueFromList(respHeaders, "Date")==null) {
                            // This is needed to avoid null values on GOTCODES
                            dateCode = Long.toString(currentTimeStampMillis);
                        }
                        List<String> foundCodes = new ArrayList<>();
                        for (String pName : SECRETCODES) {
                            if (! GOTCODES.containsKey(dateCode)) {
                                foundCodes.addAll(getMatchingParams(pName, pName, respBody, getHttpHeaderValueFromList(respHeaders, "Content-Type")));
                                foundCodes.addAll(getMatchingParams(pName, pName, getHttpHeaderValueFromList(respHeaders, "Location"), "header"));
                                foundCodes.addAll(getMatchingParams(pName, pName, respBody, "link"));
                                // Remove duplicate codes foud in same request
                                foundCodes = new ArrayList<>(new HashSet<>(foundCodes));
                                GOTCODES.put(dateCode, foundCodes);
                            }
                        }
                    }

                    // Checking for OpenID Hybrid Flow without anti-CSRF protection
                    if ( (!reqQueryParam.containsKey("state")) || (stateParameter == null)) {
                       issues.add(
                           new CustomScanIssue(
                               baseRequestResponse.getHttpService(),
                               helpers.analyzeRequest(baseRequestResponse).getUrl(),
                               new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                               "OpenID Hybrid Flow without State Parameter",
                               "The OpenID Hybrid Flow authorization request does not contains the <code>state</code> parameter.\n<br>"
                               +"Based on OpenID specifications the use of a unpredictable and unique (per user's session) "
                               +"<code>state</code> parameter, (generated from some private information about the user), "
                               +"provides a protection against CSRF attacks (as a sort of anti-CSRF token) during login procedure.\n<br>"
                               +"If this request does not have any other anti-CSRF protection then an attacker could manipulate "
                               +"the OpeniD Flow and obtain access to other users' accounts.\n<br>"
                               +"<br>References:<br>"
                               +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html\">https://openid.net/specs/openid-connect-core-1_0.html</a>",
                               "Medium",
                               "Firm"
                           )
                       );
                    } else {
                        String stateValue = stateParameter.getValue();
                        if (responseString.toLowerCase().contains(stateValue)) {
                            // Checking for OpenID Hybrid Flow with Duplicate State value issues (potential constant state values)
                            if (! GOTSTATES.isEmpty()) {
                                String respDate = getHttpHeaderValueFromList(respHeaders, "Date");
                                if (getHttpHeaderValueFromList(respHeaders, "Date") == null) {
                                    // This is needed to avoid null values on respDate
                                    respDate = Long.toString(currentTimeStampMillis);
                                }
                                // Start searching if last issued authorization code is a duplicated of already received codes
                                for (Map.Entry<String,List<String>> entry : GOTSTATES.entrySet()) {
                                    List<String> stateList = entry.getValue();
                                    String stateDate = entry.getKey();
                                    for (String stateVal: stateList) {
                                        if (responseString.toLowerCase().contains(stateVal) & (! stateDate.equals(respDate))) {
                                            // This Hybrid Flow response contains an already released State
                                            List<int[]> matches = getMatches(responseString.getBytes(), stateVal.getBytes());
                                            issues.add(
                                                new CustomScanIssue(
                                                    baseRequestResponse.getHttpService(),
                                                    helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                                    new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, matches) },
                                                    "OpenID Hybrid Flow Duplicate State Parameter Detected",
                                                    "The OpenID Hybrid Flow seems using duplicate values for the <code>state</code> parameter "
                                                    +"during login procedure.\nFor security reasons the OpenID.\n<br>"
                                                    +"Based on OpenID specifications the use of a unpredictable and unique (per user's session) "
                                                    +"<code>state</code> parameter, (generated from some private information about the user), "
                                                    +"provides a protection against CSRF attacks (as a sort of anti-CSRF token) during login procedure.\n<br>"
                                                    +"The authorization response contains the following already released <code>state</code> value <b>"+stateVal+"</b>\n<br>"
                                                    +"Using constant values for the <code>state</code> parameter de-facto disables its anti-CSRF protection.\n"
                                                    +"If the authorization request does not have any other anti-CSRF protection then an attacker could manipulate "
                                                    +"the OpeniD Flow and obtain access to other users' accounts.\n<br>"
                                                    +"Note: this issue should be <b>confirmed manually</b> by searching the duplicated <code>state</code> parameter values "
                                                    +"in the burp-proxy history.\n<br>"
                                                    +"<br>References:<br>"
                                                    +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html\">https://openid.net/specs/openid-connect-core-1_0.html</a>",
                                                    "Medium",
                                                    "Tentative"
                                                )
                                            );
                                        }
                                    }
                                }
                            }

                            // Retrieving 'state' values from OpenID Hybrid Flow responses body or Location header
                            if (!respBody.isEmpty() || respInfo.getStatusCode()==302) {
                                // Enumerate OpenID authorization codes returned by HTTP responses
                                dateCode = getHttpHeaderValueFromList(respHeaders, "Date");
                                if (getHttpHeaderValueFromList(respHeaders, "Date")==null) {
                                    // This is needed to avoid null values on GOTSTATES
                                    dateCode = Long.toString(currentTimeStampMillis);
                                }
                                List<String> foundStates = new ArrayList<>();
                                if (! GOTSTATES.containsKey(dateCode)) {
                                    foundStates.addAll(getMatchingParams("state", "state", respBody, getHttpHeaderValueFromList(respHeaders, "Content-Type")));
                                    foundStates.addAll(getMatchingParams("state", "state", getHttpHeaderValueFromList(respHeaders, "Location"), "header"));
                                    foundStates.addAll(getMatchingParams("state", "state", respBody, "link"));
                                    // Remove duplicate codes foud in same request
                                    foundStates = new ArrayList<>(new HashSet<>(foundStates));
                                    GOTSTATES.put(dateCode, foundStates);
                                }
                            } else {
                                // The response does not return the state parameter sent within the authorization request
                                // This Hybrid Flow response contains an already released State
                                List<int[]> matches = getMatches(requestString.getBytes(), stateValue.getBytes());
                                issues.add(
                                    new CustomScanIssue(
                                        baseRequestResponse.getHttpService(),
                                        helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                        new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, matches, null) },
                                        "OpenID Hybrid Flow State Parameter Mismatch Detected",
                                        "The Authorization Server does not send in response the same <code>state</code> parameter received in the authorization reqest "
                                        +"during Hybrid Flow login procedure.\n<br>"
                                        +"Based on OpenID specifications the use of a unpredictable and unique (per user's session) "
                                        +"<code>state</code> parameter, (generated from some private information about the user), "
                                        +"provides a protection against CSRF attacks (as a sort of anti-CSRF token) during login procedure.\n<br>"
                                        +"The response does not contains the <code>state</code> value <b>"+stateValue+"</b> sent within the authorization request\n<br>"
                                        +"The <code>state</code> parameter provides a protection against CSRF attacks (as a sort of anti-CSRF token) "
                                        +"for the OpenID Flow, then this misconfiguration disables it.\n<br>"
                                        +"If the authorization request does not have any other anti-CSRF protection then an attacker could manipulate "
                                        +"the OpeniD Flow and obtain access to other users' accounts.\n<br>"
                                        +"<br>References:<br>"
                                        +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html\">https://openid.net/specs/openid-connect-core-1_0.html</a>",
                                        "Medium",
                                        "Firm"
                                    )
                                );
                            }
                        }
                    }


                    // Checkibg for OpenID Hybrid Flow Misconfiguration on authorization responses
                    // the OpenID authorization response have to return the 'code' parameter with at least one of 'acces_token' or 'id_token' parameters
                    if ( (respInfo.getStatusCode()==200 || respInfo.getStatusCode()==302) & ( responseString.contains("code")) ) {
                        if ( !responseString.contains("id_token") & !responseString.contains("access_token")) {
                            issues.add(
                                new CustomScanIssue(
                                    baseRequestResponse.getHttpService(),
                                    helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                    new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                                    "OpenID Hybrid Flow Missing Tokens in Authorization Response",
                                    "The OpenID Hybrid Flow presents a misconfiguration on the returned authorization response, "
                                    +"in details both the <code>id_token</code> and the <code>access_token</code> parameters are missing.\n<br>"
                                    +"Based on OpenID Hybrid Flows specifications along with the <code>code</code> parameter the "
                                    +"authorization response have to return: the parameter <code>id_token</code> "
                                    +"when requests have the <code>response_type</code> parameter set to <b>code id_token token</b> "
                                    +"or the parameter <code>access_token</code> when requests have the <code>response_type</code> parameter set "
                                    +"to any of the values <b>code token</b> or <b>code id_token token</b>.\n<br> "
                                    +"The information contained on the <code>id_token</code> tells to the "
                                    +"Client Application that the user is authenticated (it can also give additional information "
                                    +"like his username or locale).\n<br>The absence of the <code>id_token</code> and the "
                                    +"<code>access_token</code> parameters increases the attack surface of the OpenID service.\n<br>"
                                    +"<br>References:<br>"
                                    +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html#HybridFlowSteps\">https://openid.net/specs/openid-connect-core-1_0.html#HybridFlowSteps</a>",
                                    "Medium",
                                    "Certain"
                                )
                            );   
                        }                     
                    }

                    // Checking for OpenID Hybrid Flow misconfiguration (missing nonce)
                    if (nonceParameter==null) {
                        issues.add(
                            new CustomScanIssue(
                                baseRequestResponse.getHttpService(),
                                helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                                "OpenID Hybrid Flow without Nonce Parameter",
                                "The OpenID Hybrid Flow is improperly implemented because the "
                                +"mandatory <code>nonce</code> is missing.\n This parameter is randomic and unique per "
                                +"client session in order to provide a security mitigation against replay attacks, its absence "
                                +"increases the attack surface of the OpenID service.\n<br>"
                                +"<br>References:<br>"
                                +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html#NonceNotes\">https://openid.net/specs/openid-connect-core-1_0.html#NonceNotes</a>",
                                "Low",
                                "Firm"
                            )
                        );
                    }

                    // Found OpenID Hybrid Flow 
                    issues.add(
                        new CustomScanIssue(
                            baseRequestResponse.getHttpService(),
                            helpers.analyzeRequest(baseRequestResponse).getUrl(),
                            new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                            "OpenID Hybrid Flow Detected",
                            "This is a login request of OpenID Hybrid Flow.",
                            "Information",
                            "Certain"
                        )
                    );



                // Checking OpenID Authorization Code Flow
                } else if (respType.equals("code")) {
                    // Found OpenID Authorization Code Flow 
                    issues.add(
                        new CustomScanIssue(
                            baseRequestResponse.getHttpService(),
                            helpers.analyzeRequest(baseRequestResponse).getUrl(),
                            new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                            "OpenID Authorization Code Flow Detected",
                            "This is a login request of OpenID Authorization Code Flow.",
                            "Information",
                            "Certain"
                        )
                    );
                    // Checking for Duplicate Code value issues on OpenID Authorization Code Flow
                    if (! GOTCODES.isEmpty()) {
                        String respDate = getHttpHeaderValueFromList(respHeaders, "Date");
                        if (getHttpHeaderValueFromList(respHeaders, "Date") == null) {
                            // This is needed to avoid null values on respDate
                            respDate = Long.toString(currentTimeStampMillis);
                        }
                        // Start searching if last issued authorization code is a duplicated of already received codes
                        for (Map.Entry<String,List<String>> entry : GOTCODES.entrySet()) {
                            List<String> codeList = entry.getValue();
                            String codeDate = entry.getKey();
                            for (String codeValue: codeList) {
                                if (responseString.toLowerCase().contains(codeValue) & (! codeDate.equals(respDate))) {
                                    // This Authorization Code Flow response contains an already released Code
                                    List<int[]> matches = getMatches(responseString.getBytes(), codeValue.getBytes());
                                    issues.add(
                                        new CustomScanIssue(
                                            baseRequestResponse.getHttpService(),
                                            helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                            new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, matches) },
                                            "OpenID Authorization Code Flow Duplicate Authorization Code Released",
                                            "The Authorization Server releases duplicate values for <code>code</code> parameter "
                                            +"during OpenID Authorization Code Flow login procedure.\n<br>For security reasons the OpenID "
                                            +"specifications recommend that authorization code must be unique for each user's session.\n<br>"
                                            +"The response contains the following already released <code>code</code> value <b>"+codeValue+"</b>\n<br>"
                                            +"Note: this issue should be <b>confirmed manually</b> by searching the duplicated authorization code "
                                            +"values in the burp-proxy history.\n<br>"
                                            +"<br>References:<br>"
                                            +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html#TokenRequestValidation\">https://openid.net/specs/openid-connect-core-1_0.html#TokenRequestValidation</a>",
                                            "Medium",
                                            "Firm"
                                        )
                                    );
                                }
                            }
                        }
                    }
                    // Retrieving codes from OpenID Authorization Code Flow responses body or Location header
                    if (!respBody.isEmpty() || respInfo.getStatusCode()==302) {
                        // Enumerate OpenID authorization codes returned by HTTP responses
                        dateCode = getHttpHeaderValueFromList(respHeaders, "Date");
                        if (getHttpHeaderValueFromList(respHeaders, "Date")==null) {
                            // This is needed to avoid null values on GOTCODES
                            dateCode = Long.toString(currentTimeStampMillis);
                        }
                        List<String> foundCodes = new ArrayList<>();
                        for (String pName : SECRETCODES) {
                            if (! GOTCODES.containsKey(dateCode)) {
                                foundCodes.addAll(getMatchingParams(pName, pName, respBody, getHttpHeaderValueFromList(respHeaders, "Content-Type")));
                                foundCodes.addAll(getMatchingParams(pName, pName, getHttpHeaderValueFromList(respHeaders, "Location"), "header"));
                                foundCodes.addAll(getMatchingParams(pName, pName, respBody, "link"));
                                // Remove duplicate codes foud in same request
                                foundCodes = new ArrayList<>(new HashSet<>(foundCodes));
                                GOTCODES.put(dateCode, foundCodes);
                            }
                        }
                    }



                    // Checking for OpenID Authorization Code Flow without anti-CSRF protection
                    if ( (!reqQueryParam.containsKey("state")) || (stateParameter == null)) {
                        issues.add(
                            new CustomScanIssue(
                                baseRequestResponse.getHttpService(),
                                helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                                "OpenID Authorization Code Flow without State Parameter Detected",
                                "The OpenID Authorization Code Flow login request does not contains the <code>state</code> parameter.\n<br>"
                                +"Based on OpenID specifications the use of a unpredictable and unique (per user's session) "
                                +"<code>state</code> parameter, (generated from some private information about the user), "
                                +"provides a protection against CSRF attacks (as a sort of anti-CSRF token) during login procedure.\n<br>"
                                +"If this request does not have any other anti-CSRF protection then an attacker could manipulate "
                                +"the OpeniD Flow and obtain access to other users' accounts.\n<br>"
                                +"<br>References:<br>"
                                +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html\">https://openid.net/specs/openid-connect-core-1_0.html</a>",
                                "Medium",
                                "Firm"
                            )
                        );
                    } else {
                        String stateValue = stateParameter.getValue();
                        if (responseString.toLowerCase().contains(stateValue)) {
                            // Checking for OpenID Authorization Code Flow with Duplicate State value issues (potential constant state values)
                            if (! GOTSTATES.isEmpty()) {
                                String respDate = getHttpHeaderValueFromList(respHeaders, "Date");
                                if (getHttpHeaderValueFromList(respHeaders, "Date") == null) {
                                    // This is needed to avoid null values on respDate
                                    respDate = Long.toString(currentTimeStampMillis);
                                }
                                // Start searching if last issued authorization code is a duplicated of already received codes
                                for (Map.Entry<String,List<String>> entry : GOTSTATES.entrySet()) {
                                    List<String> stateList = entry.getValue();
                                    String stateDate = entry.getKey();
                                    for (String stateVal: stateList) {
                                        if (responseString.toLowerCase().contains(stateVal) & (! stateDate.equals(respDate))) {
                                            // This Authorization Code Flow response contains an already released State
                                            List<int[]> matches = getMatches(responseString.getBytes(), stateVal.getBytes());
                                            issues.add(
                                                new CustomScanIssue(
                                                    baseRequestResponse.getHttpService(),
                                                    helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                                    new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, matches) },
                                                    "OpenID Authorization Code Flow Duplicate State Parameter Detected",
                                                    "The OpenID Authorization Code Flow seems using duplicate values for the <code>state</code> parameter "
                                                    +"during login procedure.\nFor security reasons the OpenID.\n<br>"
                                                    +"Based on OpenID specifications the use of a unpredictable and unique (per user's session) "
                                                    +"<code>state</code> parameter, (generated from some private information about the user), "
                                                    +"provides a protection against CSRF attacks (as a sort of anti-CSRF token) during login procedure.\n<br>"
                                                    +"The authorization response contains the following already released <code>state</code> value <b>"+stateVal+"</b>\n<br>"
                                                    +"Using constant values for the <code>state</code> parameter de-facto disables its anti-CSRF protection.\n"
                                                    +"If the authorization request does not have any other anti-CSRF protection then an attacker could manipulate "
                                                    +"the OpeniD Flow and obtain access to other users' accounts.\n<br>"
                                                    +"Note: this issue should be <b>confirmed manually</b> by searching the duplicated <code>state</code> parameter values "
                                                    +"in the burp-proxy history.\n<br>"
                                                    +"<br>References:<br>"
                                                    +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html\">https://openid.net/specs/openid-connect-core-1_0.html</a>",
                                                    "Medium",
                                                    "Tentative"
                                                )
                                            );
                                        }
                                    }
                                }
                            }

                            // Retrieving 'state' values from OpenID Authorization Code Flow responses body or Location header
                            if (!respBody.isEmpty() || respInfo.getStatusCode()==302) {
                                // Enumerate OpenID authorization codes returned by HTTP responses
                                dateCode = getHttpHeaderValueFromList(respHeaders, "Date");
                                if (getHttpHeaderValueFromList(respHeaders, "Date")==null) {
                                    // This is needed to avoid null values on GOTSTATES
                                    dateCode = Long.toString(currentTimeStampMillis);
                                }
                                List<String> foundStates = new ArrayList<>();
                                if (! GOTSTATES.containsKey(dateCode)) {
                                    foundStates.addAll(getMatchingParams("state", "state", respBody, getHttpHeaderValueFromList(respHeaders, "Content-Type")));
                                    foundStates.addAll(getMatchingParams("state", "state", getHttpHeaderValueFromList(respHeaders, "Location"), "header"));
                                    foundStates.addAll(getMatchingParams("state", "state", respBody, "link"));
                                    // Remove duplicate codes foud in same request
                                    foundStates = new ArrayList<>(new HashSet<>(foundStates));
                                    GOTSTATES.put(dateCode, foundStates);
                                }
                            } else {
                                // The response does not return the state parameter sent within the authorization request
                                // This Authorization Code Flow response contains an already released State
                                List<int[]> matches = getMatches(requestString.getBytes(), stateValue.getBytes());
                                issues.add(
                                    new CustomScanIssue(
                                        baseRequestResponse.getHttpService(),
                                        helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                        new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, matches, null) },
                                        "OpenID Authorization Code Flow State Parameter Mismatch Detected",
                                        "The Authorization Server does not send in response the same <code>state</code> parameter received in the authorization reqest "
                                        +"during Authorization Code Flow login procedure.\n<br>"
                                        +"Based on OpenID specifications the use of a unpredictable and unique (per user's session) "
                                        +"<code>state</code> parameter, (generated from some private information about the user), "
                                        +"provides a protection against CSRF attacks (as a sort of anti-CSRF token) during login procedure.\n<br>"
                                        +"The response does not contains the <code>state</code> value <b>"+stateValue+"</b> sent within the authorization request\n<br>"
                                        +"The <code>state</code> parameter provides a protection against CSRF attacks (as a sort of anti-CSRF token) "
                                        +"for the OpenID Flow, then this misconfiguration disables it.\n<br>"
                                        +"If the authorization request does not have any other anti-CSRF protection then an attacker could manipulate "
                                        +"the OpeniD Flow and obtain access to other users' accounts.\n<br>"
                                        +"<br>References:<br>"
                                        +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html\">https://openid.net/specs/openid-connect-core-1_0.html</a>",
                                        "Medium",
                                        "Firm"
                                    )
                                );
                            }
                        }
                    }

                    // Checking for OpenID Authorization Code Flow misconfiguration (missing nonce)
                    if (nonceParameter==null) {
                        issues.add(
                            new CustomScanIssue(
                                baseRequestResponse.getHttpService(),
                                helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                                "OpenID Authorization Code Flow without Nonce Parameter",
                                "The OpenID Authorization Code Flow is improperly implemented because the "
                                +"mandatory <code>nonce</code> is missing.\n This parameter is randomic and unique per "
                                +"client session in order to provide a security mitigation against replay attacks, its absence "
                                +"increases the attack surface of the OpenID service.\n<br>"
                                +"<br>References:<br>"
                                +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html#NonceNotes\">https://openid.net/specs/openid-connect-core-1_0.html#NonceNotes</a>",
                                "Low",
                                "Firm"
                            )
                        );
                    }

                    // Checking for OpenID Authorization Code Flow without PKCE protection
                    if ((!reqQueryParam.containsKey("code_challenge")) || (challengeParameter == null)) {
                        issues.add(
                            new CustomScanIssue(
                                baseRequestResponse.getHttpService(),
                                helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                                "OpenID Authorization Code Flow without PKCE Protection Detected",
                                "The Authorization Code Flow login request does not have the <code>code_challenge</code> parameter, "
                                +"then is not implemented with PKCE protections against authorization code interception.\n<br>"
                                +"In Mobile, Native desktop and SPA contexts is a security requirement to use OpenID Authorization Code Flow with PKCE extension "
                                +"or alternatively to use OpenID Hybrid Flow.\n<br>"
                                +"<br>References:<br>"
                                +"<a href=\"https://openid.net/specs/openid-igov-oauth2-1_0-02.html#rfc.section.3.1.7\">https://openid.net/specs/openid-igov-oauth2-1_0-02.html#rfc.section.3.1.7</a>",
                                "Medium",
                                "Firm"
                            )
                        );
                    // Checking for OpenID Authorization Code Flow PKCE misconfiguration
                    } else if ((reqQueryParam.containsKey("code_challenge_method")) || (challengemethodParameter != null)) {
                        if (reqQueryParam.get("code_challenge_method").equals("plain") || challengemethodParameter.getValue().equals("plain")) {
                            List<int[]> matches = getMatches(requestString.getBytes(), "plain".getBytes());
                            issues.add(
                                new CustomScanIssue(
                                    baseRequestResponse.getHttpService(),
                                    helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                    new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, matches, null) },
                                    "OpenID Authorization Code Flow with PKCE Plaintext",
                                    "The Authorization Code Flow with PKCE is configured with the "
                                    +"<code>code_challenge_method</code> parameter set to <b>plain</b>.\n<br>"
                                    +"This means that the secret <code>code_verifier</code> value is sent plaintext as "
                                    +"<code>code_challenge</code> parameter on authorization requests and "
                                    +"then PKCE protections against authorization code interception attacks are de-facto disabled, because "
                                    +"they are based on the secrecy of the <code>code_verifier</code> parameter sent within requests.\n<br>"
                                    +"In Mobile, Native desktop and SPA contexts is a security requirement to use OpenID Authorization Code Flow with PKCE extension "
                                    +"or alternatively to use OpenID Hybrid Flow.\n<br>"
                                    +"<br>References:<br>"
                                    +"<a href=\"https://openid.net/specs/openid-igov-oauth2-1_0-02.html#rfc.section.3.1.7\">https://openid.net/specs/openid-igov-oauth2-1_0-02.html#rfc.section.3.1.7</a>",
                                    "Medium",
                                    "Firm"
                                )
                            );
                        }
                    }
                }
            }

        // Starting passive checks for OAUTHv2 issues
        } else {
            // First search for OAUTHv2 Implicit or Authorization Code Flows
            if ( ((reqQueryParam!=null & reqQueryParam.containsKey("client_id") & reqQueryParam.containsKey("response_type")) || 
            ( reqParam!=null & (clientidParameter != null) & (resptypeParameter!=null))) ) {
                stdout.println("[+] Passive Scan: OAUTHv2 Implicit or Authorization Code Flows detected");
                if (reqQueryParam.containsKey("redirect_uri") & reqQueryParam.containsKey("response_type")) {
                    respType = reqQueryParam.get("response_type");
                    redirUri = reqQueryParam.get("redirect_uri");
                } else if ((redirParameter != null) & (resptypeParameter!=null)) {
                    respType = resptypeParameter.getValue();
                    redirUri = redirParameter.getValue();
                }


                // Check for weak OAUTHv2 state values (i.e. insufficient length, only alphabetic, only numeric, etc.)
                if (stateParameter!=null) {
                    String stateValue = stateParameter.getValue();
                    if ( (stateValue.length() < 5) || ( (stateValue.length() < 7) & ((stateValue.matches("[a-zA-Z]+")) || (stateValue.matches("[0-9]+")))) ) {
                        List<int[]> requestHighlights = new ArrayList<>(1);
                        int[] stateOffset = new int[2];
                        int stateStart = requestString.indexOf(stateValue);
                        stateOffset[0] = stateStart;
                        stateOffset[1] = stateStart+stateValue.length();
                        requestHighlights.add(stateOffset);
                        issues.add(
                            new CustomScanIssue(
                                baseRequestResponse.getHttpService(),
                                helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, requestHighlights, null) },
                                "OAUTHv2 Flow with Weak State Parameter",
                                "The OAUTHv2 Flow presents a security misconfiguration because is using weak values for"
                                +"the <code>state</code> parameter.\n<br> "
                                +"In details the OAUTHv2 Flow request contains a <code>state</code> value of <b>"+stateValue+"</b>.\n<br>"
                                +"Based on OAUTHv2 specifications the use of a unpredictable and unique (per user's session) "
                                +"<code>state</code> parameter, (generated from some private information about the user), "
                                +"provides a protection against CSRF attacks (as a sort of anti-CSRF token) during login procedure.\n<br>"
                                +"When the <code>state</code> value is guessable (insufficient entropy) "
                                +"then the attack surface of the OAUTHv2 service increases.\n<br>"
                                +"<br>References:<br>"
                                +"<a href=\"https://datatracker.ietf.org/doc/html/rfc6819#page-13\">https://datatracker.ietf.org/doc/html/rfc6819#page-13</a>",
                                "Low",
                                "Firm"
                            )
                        );
                    }
                }


                // Checking for OAUTHv2 Flow with 'request_uri' parameter
                if (requesturiParameter!=null) {
                    issues.add(
                        new CustomScanIssue(
                            baseRequestResponse.getHttpService(),
                            helpers.analyzeRequest(baseRequestResponse).getUrl(),
                            new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                            "OAUTHv2 Flow with Request_Uri Parameter Detected",
                            "The OAUTHv2 Flow uses the parameter <code>request_uri</code> to "
                            +"enable the retrieving of client's Request-Object via a URI reference to it.\n<br>"
                            +"Based on OAUTHv2 specifications the value of the <code>request_uri</code> parameter "
                            +"is set to an URI pointing to a server hosting a JWT which contains the client's parameter values. "
                            +"In this way the OAUTHv2 Provider can fetch the provided URI and retrieve the Request-Object "
                            +"by parsing the JWT contents.\n<br>"
                            +"For security reasons the URI value of <code>request_uri</code> parameter should be carefully validated "
                            +"at server-side, otherwise a threat agent could be able to lead the OAUTHv2 Provider to interact with "
                            +"an arbitrary server under is control and then potentially exploit SSRF vulnerabilities.\n<br>"
                            +"As mitigation the OAUTHv2 Provider should define a whitelist of allowed URI values (pre-registered "
                            +"during the client registration process) for the <code>request_uri</code> parameter.\n<br>"
                            +"<br>References:<br>"
                            +"<a href=\"https://tools.ietf.org/html/draft-lodderstedt-oauth-par\">https://tools.ietf.org/html/draft-lodderstedt-oauth-par</a>"
                            +"<a href=\"https://portswigger.net/research/hidden-oauth-attack-vectors\">https://portswigger.net/research/hidden-oauth-attack-vectors</a>",
                            "Information",
                            "Certain"
                        )
                    );
                }


                // Checking for OAUTHv2 Implicit Flow
                if (respType.equals("token")) {
                    // Found the insecure OAUTHv2 Implicit Flow 
                    issues.add(
                        new CustomScanIssue(
                            baseRequestResponse.getHttpService(),
                            helpers.analyzeRequest(baseRequestResponse).getUrl(),
                            new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                            "OAUTHv2 Implicit Flow Insecure Implementation Detected",
                            "The OAUTHv2 Implicit Flow is considered inerently insecure because enables the transmission of "
                            +"access tokens in the URL of HTTP GET requests.\n<br>.This behaviour is deprecated by OAUTHv2 specifications "
                            +"since it exposes to various security issues as leakages (i.e. via cache, traffic sniffing, etc.) and replay "
                            +"attacks of access tokens.\n<br>It is suggested to adopt OAUTHv2 Authorization Code Flow, or "
                            +"any of the specific OpenID Implicyt Flow implementations (as <b>id_token</b> or <b>form_post</b>).\n<br>"
                            +"Note: the use of Implicit Flow is also considered insecure in Mobile application contexts.\n<br>"
                            +"<br>References:<br>"
                            +"<a href=\"https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics-09#page-5\">https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics-09#page-5</a>"
                            +"<a href=\"https://tools.ietf.org/id/draft-parecki-oauth-browser-based-apps-02.txt\">https://tools.ietf.org/id/draft-parecki-oauth-browser-based-apps-02.txt</a>",
                            "Medium",
                            "Certain"
                        )
                    );


                    // Checking for Refresh token included in login response (Location header or body) that is discouraged for Implicit Flow
                    foundRefresh = false;
                    if (respBody.toLowerCase().contains("refresh")) {
                            foundRefresh = true;
                    } else if (getHttpHeaderValueFromList(respHeaders, "Location")!=null) {
                        if (getHttpHeaderValueFromList(respHeaders, "Location").toLowerCase().contains("refresh")) {
                            foundRefresh = true;
                        }
                    }
                    if (foundRefresh) {
                        issues.add(
                            new CustomScanIssue(
                                baseRequestResponse.getHttpService(),
                                helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                                "OAUTHv2 Implicit Flow Improper Release of Refresh Token",
                                "The Resource Server releases a refresh token after successful Implicit Flow login. "
                                +"This behaviour is deprecated by OAUTHv2 specifications for Implicit Flow, also consider that "
                                +"the use of OAUTHv2 Implicit Flow is insecure and should be avoided.\n<br>"
                                +"<br>References:<br>"
                                +"<a href=\"https://datatracker.ietf.org/doc/html/rfc6749#section-4.2\">https://datatracker.ietf.org/doc/html/rfc6749#section-4.2</a>"
                                +"<a href=\"https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics-09#page-5\">https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics-09#page-5</a>"
                                +"<a href=\"https://tools.ietf.org/id/draft-parecki-oauth-browser-based-apps-02.txt\">https://tools.ietf.org/id/draft-parecki-oauth-browser-based-apps-02.txt</a>",
                                "Medium",
                                "Certain"
                            )
                        );
                    }
                
                // Checking for OAUTHv2 Authorization Code Flow
                } else if (respType.equals("code")) {
                    // Found OAUTHv2 Authorization Code Flow 
                    issues.add(
                        new CustomScanIssue(
                            baseRequestResponse.getHttpService(),
                            helpers.analyzeRequest(baseRequestResponse).getUrl(),
                            new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                            "OAUTHv2 Authorization Code Flow Detected",
                            "This is a login request of OAUTHv2 Authorization Code Flow.",
                            "Information",
                            "Certain"
                        )
                    );
                    // Checking for Duplicate Code value issues on OAUTHv2 Authorization Code Flow
                    if (! GOTCODES.isEmpty()) {
                        String respDate = getHttpHeaderValueFromList(respHeaders, "Date");
                        if (getHttpHeaderValueFromList(respHeaders, "Date") == null) {
                            // This is needed to avoid null values on respDate
                            respDate = Long.toString(currentTimeStampMillis);
                        }
                        // Start searching if last issued authorization code is a duplicated of already received codes
                        for (Map.Entry<String,List<String>> entry : GOTCODES.entrySet()) {
                            List<String> codeList = entry.getValue();
                            String codeDate = entry.getKey();
                            for (String codeValue : codeList) {
                                if (responseString.toLowerCase().contains(codeValue) & (! codeDate.equals(respDate))) {
                                    // This Authorization Code Flow response contains an already released Code
                                    List<int[]> matches = getMatches(responseString.getBytes(), codeValue.getBytes());
                                    issues.add(
                                        new CustomScanIssue(
                                            baseRequestResponse.getHttpService(),
                                            helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                            new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, matches) },
                                            "OAUTHv2 Authorization Code Flow Duplicate Authorization Code Released",
                                            "The Authorization Server releases duplicate values for <code>code</code> parameter "
                                            +"during OAUTHv2 Authorization Code Flow login procedure.\n<br>For security reasons the OAUTHv2 "
                                            +"specifications recommend that authorization code must be unique for each user's session.\n<br>"
                                            +"The response contains the following already released <code>code</code> value <b>"+codeValue+"</b>\n<br>"
                                            +"Note: this issue should be <b>confirmed manually</b> by searching the duplicated authorization code "
                                            +"values in the burp-proxy history.\n<br>"
                                            +"<br>References:<br>"
                                            +"<a href=\"https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2\">https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2</a>",
                                            "Medium",
                                            "Firm"
                                        )
                                    );
                                }
                            }
                        }
                    }

                    // Retrieving codes from OAUTHv2 Authorization Code Flow responses body or Location header
                    if (!respBody.isEmpty() || respInfo.getStatusCode()==302) {
                        // Enumerate OAUTHv2 authorization codes returned by HTTP responses
                        dateCode = getHttpHeaderValueFromList(respHeaders, "Date");
                        if (getHttpHeaderValueFromList(respHeaders, "Date")==null) {
                            // This is needed to avoid null values on GOTCODES
                            dateCode = Long.toString(currentTimeStampMillis);
                        }
                        List<String> foundCodes = new ArrayList<>();
                        for (String pName : SECRETCODES) {
                            if (! GOTCODES.containsKey(dateCode)) {
                                foundCodes.addAll(getMatchingParams(pName, pName, respBody, getHttpHeaderValueFromList(respHeaders, "Content-Type")));
                                foundCodes.addAll(getMatchingParams(pName, pName, getHttpHeaderValueFromList(respHeaders, "Location"), "header"));
                                foundCodes.addAll(getMatchingParams(pName, pName, respBody, "link"));
                                // Remove duplicate codes foud in same request
                                foundCodes = new ArrayList<>(new HashSet<>(foundCodes));
                                GOTCODES.put(dateCode, foundCodes);
                            }
                        }
                    }



                    // Checking for OAUTHv2 Authorization Code Flow without anti-CSRF protection            
                    if ( (!reqQueryParam.containsKey("state")) || (stateParameter == null)) {
                        issues.add(
                            new CustomScanIssue(
                                baseRequestResponse.getHttpService(),
                                helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                                "OAUTHv2 Authorization Code Flow without State Parameter Detected",
                                "The Authorization Code Flow login request does not have the <code>state</code> parameter.\n<br>"
                                +"The use of a unpredictable and unique (per user's session) <code>state</code> parameter value, "
                                +"provides a protection against CSRF attacks (as an anti-CSRF token) during Authorization Code Flow login procedure.\n<br>"
                                +"If the authorization request does not have any other anti-CSRF protection then an attacker could manipulate "
                                +"the OAUTHv2 Flow and obtain access to other users' accounts.\n<br>"
                                +"<br>References:<br>"
                                +"<a href=\"https://datatracker.ietf.org/doc/html/rfc6819#page-13\">https://datatracker.ietf.org/doc/html/rfc6819#page-13</a>",
                                "Medium",
                                "Firm"
                            )
                        );
                    } else {
                        // Go here when OAUTHv2 Authorization Code request contains a 'state' parameter 
                        String stateValue = stateParameter.getValue();
                        if (responseString.toLowerCase().contains(stateValue)) {
                            // Checking for OAUTHv2 Authorization Code Flow with Duplicate State value issues (potential constant state values)
                            if (! GOTSTATES.isEmpty()) {
                                String respDate = getHttpHeaderValueFromList(respHeaders, "Date");
                                if (getHttpHeaderValueFromList(respHeaders, "Date") == null) {
                                    // This is needed to avoid null values on respDate
                                    respDate = Long.toString(currentTimeStampMillis);
                                }
                                // Start searching if last issued authorization code is a duplicated of already received codes
                                for (Map.Entry<String,List<String>> entry : GOTSTATES.entrySet()) {
                                    List<String> stateList = entry.getValue();
                                    String stateDate = entry.getKey();
                                    for (String stateVal: stateList) {
                                        if (responseString.toLowerCase().contains(stateVal) & (! stateDate.equals(respDate))) {
                                            // This Authorization Code Flow response contains an already released State
                                            List<int[]> matches = getMatches(responseString.getBytes(), stateVal.getBytes());
                                            issues.add(
                                                new CustomScanIssue(
                                                    baseRequestResponse.getHttpService(),
                                                    helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                                    new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, matches) },
                                                    "OAUTHv2 Authorization Code Flow Duplicate State Parameter Detected",
                                                    "The OAUTHv2 Authorization Code Flow seems using duplicate values for the <code>state</code> parameter "
                                                    +"during login procedure.\nFor security reasons the OAUTHv2.\n<br>"
                                                    +"Based on OAUTHv2 specifications the use of a unpredictable and unique (per user's session) "
                                                    +"<code>state</code> parameter, (generated from some private information about the user), "
                                                    +"provides a protection against CSRF attacks (as a sort of anti-CSRF token) during login procedure.\n<br>"
                                                    +"The authorization response contains the following already released <code>state</code> value <b>"+stateVal+"</b>\n<br>"
                                                    +"Using constant values for the <code>state</code> parameter de-facto disables its anti-CSRF protection.\n"
                                                    +"If the authorization request does not have any other anti-CSRF protection then an attacker could manipulate "
                                                    +"the OAUTHv2 Flow and obtain access to other users' accounts.\n<br>"
                                                    +"Note: this issue should be <b>confirmed manually</b> by searching the duplicated <code>state</code> parameter values "
                                                    +"in the burp-proxy history.\n<br>"
                                                    +"<br>References:<br>"
                                                    +"<a href=\"https://datatracker.ietf.org/doc/html/rfc6819#page-13\">https://datatracker.ietf.org/doc/html/rfc6819#page-13</a>",
                                                    "Medium",
                                                    "Tentative"
                                                )
                                            );
                                        }
                                    }
                                }
                            }

                            // Retrieving 'state' values from OAUTHv2 Authorization Code Flow responses body or Location header
                            if (!respBody.isEmpty() || respInfo.getStatusCode()==302) {
                                // Enumerate OAUTHv2 authorization codes returned by HTTP responses
                                dateCode = getHttpHeaderValueFromList(respHeaders, "Date");
                                if (getHttpHeaderValueFromList(respHeaders, "Date")==null) {
                                    // This is needed to avoid null values on GOTSTATES
                                    dateCode = Long.toString(currentTimeStampMillis);
                                }
                                List<String> foundStates = new ArrayList<>();
                                if (! GOTSTATES.containsKey(dateCode)) {
                                    foundStates.addAll(getMatchingParams("state", "state", respBody, getHttpHeaderValueFromList(respHeaders, "Content-Type")));
                                    foundStates.addAll(getMatchingParams("state", "state", getHttpHeaderValueFromList(respHeaders, "Location"), "header"));
                                    foundStates.addAll(getMatchingParams("state", "state", respBody, "link"));
                                    // Remove duplicate codes foud in same request
                                    foundStates = new ArrayList<>(new HashSet<>(foundStates));
                                    GOTSTATES.put(dateCode, foundStates);
                                }
                            } else {
                                // The Authorization Code Flow response does not return the state parameter sent within the authorization request
                                List<int[]> matches = getMatches(requestString.getBytes(), stateValue.getBytes());
                                issues.add(
                                    new CustomScanIssue(
                                        baseRequestResponse.getHttpService(),
                                        helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                        new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, matches, null) },
                                        "OAUTHv2 Authorization Code Flow State Parameter Mismatch Detected",
                                        "The Authorization Server does not send in response the same <code>state</code> parameter received in the authorization reqest "
                                        +"during Authorization Code Flow login procedure.\n<br>"
                                        +"The authorization response does not contains the <code>state</code> value <b>"+stateValue+"</b> sent within the authorization request\n<br>"
                                        +"Based on OAUTHv2 specifications the use of a unpredictable and unique (per user's session) "
                                        +"<code>state</code> parameter (generated from some private information about the user), "
                                        +"provides a protection against CSRF attacks (as a sort of anti-CSRF token) during login procedure.\n<br>"
                                        +"Then for security reasons this mechanism requires that when the Authorization Server receives a <code>state</code> parameter "
                                        +"its response must contain the same <code>state</code> value, then this misconfiguration disables its anti-CSRF protection.\n<br>"
                                        +"If the authorization request does not have any other anti-CSRF protection  then an attacker could manipulate "
                                        +"the OAUTHv2 Flow and obtain access to other users' accounts.\n<br>"
                                        +"<br>References:<br>"
                                        +"<a href=\"https://datatracker.ietf.org/doc/html/rfc6819#page-13\">https://datatracker.ietf.org/doc/html/rfc6819#page-13</a>",
                                        "Medium",
                                        "Firm"
                                    )
                                );
                            }
                        }
                    }




                    // Checkinf for OAUTHv2 Authorization Code Flow without PKCE protection
                    if ((!reqQueryParam.containsKey("code_challenge")) || (challengeParameter == null)) {
                        issues.add(
                            new CustomScanIssue(
                                baseRequestResponse.getHttpService(),
                                helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                                "OAUTHv2 Authorization Code Flow without PKCE Protection",
                                "The Authorization Code Flow login request does not have the <code>code_challenge</code> parameter, "
                                +"then is not implemented with PKCE protections against authorization code interception.\n<br>"
                                +"The OAUTHv2 Authorization Code Flow with PKCE provides protection against authorization code interception attacks, "
                                +"and is a security requirement on Mobile contexts.\n<br>"
                                +"In Mobile, Native desktop and SPA contexts is a security requirement to use OAUTHv2 Authorization Code Flow with PKCE extension.\n<br>"
                                +"<br>References:<br>"
                                +"<a href=\"https://datatracker.ietf.org/doc/html/rfc7636\">https://datatracker.ietf.org/doc/html/rfc7636</a>",
                                "Medium",
                                "Firm"
                            )
                        );
                    // Checking for OAUTHv2 Authorization Code Flow PKCE misconfiguration
                    } else if ((reqQueryParam.containsKey("code_challenge_method")) || (challengemethodParameter != null)) {
                        if (reqQueryParam.get("code_challenge_method").equals("plain") || challengemethodParameter.getValue().equals("plain")) {
                            List<int[]> matches = getMatches(requestString.getBytes(), "plain".getBytes());
                            issues.add(
                                new CustomScanIssue(
                                    baseRequestResponse.getHttpService(),
                                    helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                    new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, matches, null) },
                                    "OAUTHv2 Authorization Code Flow with PKCE Plaintext",
                                    "The Authorization Code Flow with PKCE is configured with the "
                                    +"<code>code_challenge_method</code> parameter set to <b>plain</b>.\n<br>"
                                    +"This means that the secret <code>code_verifier</code> value is sent plaintext on requests "
                                    +"then PKCE protections against authorization code interception attacks are de-facto disabled, because "
                                    +"they are based on the secrecy of the <code>code_verifier</code> parameter sent within requests.\n<br>"
                                    +"In Mobile, Native desktop and SPA contexts is a security requirement to use OAUTHv2 Authorization Code Flow with PKCE extension.\n<br>"
                                    +"<br>References:<br>"
                                    +"<a href=\"https://datatracker.ietf.org/doc/html/rfc7636\">https://datatracker.ietf.org/doc/html/rfc7636</a>",
                                    "Medium",
                                    "Firm"
                                )
                            );
                        }
                    }
                } 
                
            // Then search for OAUTHv2 Resource Owner Password Credentials or Client Credentials Flows
            } else if (reqParam!=null & grantParameter != null) {
                stdout.println("[+] Passive Scan: OAUTHv2 Resource Owner Password Credentials or Client Credentials Flows detected");
                // First retrieves the grant_type parameter from request body
                String grantType = "";
                for (IParameter param: reqParam) {
                    if (param.getType() == IParameter.PARAM_BODY) {
                        if (param.getName().equals("grant_type")) {
                            grantType = param.getValue();
                        }
                    }
                }

                // Checking for OAUTHv2 Resource Owner Password Credentials Flow
                if (grantType.equals("password")) {
                    // Found OAUTHv2 Resource Owner Password Credentials Flow
                    issues.add(
                        new CustomScanIssue(
                            baseRequestResponse.getHttpService(),
                            helpers.analyzeRequest(baseRequestResponse).getUrl(),
                            new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                            "OAUTHv2 Resource Owner Password Credentials Flow Detected",
                            "This is a Resource Owner Password Credentials Flow login request.\n<br>"
                            +"Note: in Mobile application contexts the Resource Owner Password Credentials Flow should be implemented "
                            +"only when both Client Application and Authorization Server belong to the same provider.",
                            "Information",
                            "Certain"
                        )
                    );

                // Checking OAUTHv2 Client Credentials Flow
                } else if (grantType.equals("client_credentials")) {
                    // Checking if Refresh token is released in login response (Location header or body) that is discouraged for Client Credentials Flow
                    foundRefresh = false;
                    if (respBody.toLowerCase().contains("refresh")) {
                            foundRefresh = true;
                    } else if (getHttpHeaderValueFromList(respHeaders, "Location")!=null) {
                        if (getHttpHeaderValueFromList(respHeaders, "Location").toLowerCase().contains("refresh")) {
                            foundRefresh = true;
                        }
                    }
                    if (foundRefresh) {
                        issues.add(
                            new CustomScanIssue(
                                baseRequestResponse.getHttpService(),
                                helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                                "OAUTHv2 Client Credentials Flow Improper Release of Refresh Token",
                                "The Resource Server releases a refresh token after sucessful Client Credentials Flow login, "
                                +"this practice is discouraged by OAUTHv2 specifications.\n<br>"
                                +"<br>References:<br>"
                                +"<a href=\"https://datatracker.ietf.org/doc/html/rfc6749#section-4.4.3\">https://datatracker.ietf.org/doc/html/rfc6749#section-4.4.3</a>",
                                "Low",
                                "Certain"
                            )
                        );                           
                    } else {
                        // Found OAUTHv2 Client Credentials Flow
                        issues.add(
                            new CustomScanIssue(
                                baseRequestResponse.getHttpService(),
                                helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, null) },
                                "OAUTHv2 Client Credentials Flow Detected",
                                "This is a Client Credentials Flow login request.\n<br>"
                                +"Normally it is used by clients to obtain an access token outside of the context of a user (i.e. Machine-to-Machine).",
                                "Information",
                                "Certain"
                            )
                        );
                    }
                }
            }
        }

        // Additional checks for Secret Token Leakage issues
        int[] findingOffset = new int[2];
        if (! GOTTOKENS.isEmpty()) {
            String reqReferer = getHttpHeaderValueFromList(reqHeaders, "Referer");
            for (Map.Entry<String,List<String>> entry : GOTTOKENS.entrySet()) {
                List<String> tokenList = entry.getValue();
                for (String tokenValue: tokenList) {
                    if (reqReferer!=null) {
                        if (reqReferer.contains(tokenValue)) {
                            // Found Code Leakage issue on Referer header
                            List<int[]> requestHighlights = new ArrayList<>(1);
                            int findingStart = requestString.indexOf(reqReferer);
                            findingOffset[0] = findingStart;
                            findingOffset[1] = findingStart+reqReferer.length();
                            requestHighlights.add(findingOffset);                            
                            issues.add(
                                new CustomScanIssue(
                                    baseRequestResponse.getHttpService(),
                                    helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                    new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, requestHighlights, null) },
                                    "OAUTHv2/OpenID Leakage of Secret Token on Referer Header",
                                    "The request improperly exposes the following secret token (Access Token or Refresh Token) "
                                    +"on its Referer header: <b>"+tokenValue+"</b>, then a threat agent could be able retrieve it and "
                                    +"obtain access to private resources of victim users.",
                                    "Medium",
                                    "Firm"
                                )
                            );
                        }
                    }
                    if (!reqQueryString.isEmpty() & reqQueryString.contains(tokenValue)) {
                        // Found Code Leakage issue in URL query
                        List<int[]> requestHighlights = new ArrayList<>(1);
                        int findingStart = requestString.indexOf(tokenValue);
                        findingOffset[0] = findingStart;
                        findingOffset[1] = findingStart+tokenValue.length();
                        requestHighlights.add(findingOffset);
                        issues.add(
                            new CustomScanIssue(
                                baseRequestResponse.getHttpService(),
                                helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, requestHighlights, null) },
                                "OAUTHv2/OpenID Leakage of Secret Token in URL Query",
                                "The request improperly exposes the following secret token (Access Token or Refresh Token) "
                                +"value on its URL query string: <b>"+tokenValue+"</b>, then a threat agent could be able retrieve it and "
                                +"obtain access to private resources of victim users.",
                                "Medium",
                                "Firm"
                            )
                        );
                    }
                }                
            }
        }
        // Additional checks for OpenID Id_Token Leakage issues
        if (! GOTOPENIDTOKENS.isEmpty()) {
            String reqReferer = getHttpHeaderValueFromList(reqHeaders, "Referer");
            List<String> idtokenList = GOTOPENIDTOKENS;
            for (String idtokenValue: idtokenList) {
                if (reqReferer!=null) { 
                    if (reqReferer.contains(idtokenValue)) {
                        // Found Token Leakage issue on Referer header
                        List<int[]> requestHighlights = new ArrayList<>(1);
                        int findingStart = requestString.indexOf(reqReferer);
                        findingOffset[0] = findingStart;
                        findingOffset[1] = findingStart+reqReferer.length();
                        requestHighlights.add(findingOffset);
                        issues.add(
                            new CustomScanIssue(
                                baseRequestResponse.getHttpService(),
                                helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, requestHighlights, null) },
                                "OpenID Leakage of ID_Token on Referer Header",
                                "The request improperly exposes the following OpenID <code>id_token</code> "
                                +"on its Referer header: <b>"+idtokenValue+"</b>, then a threat agent could be able retrieve it and "
                                +"obtain access to private resources of victim users.",
                                "Medium",
                                "Firm"
                            )
                        );
                    }
                }
                if (!reqQueryString.isEmpty() & reqQueryString.contains(idtokenValue)) {
                    // Found Token Leakage issue in URL query
                    List<int[]> requestHighlights = new ArrayList<>(1);
                    int findingStart = requestString.indexOf(idtokenValue);
                    findingOffset[0] = findingStart;
                    findingOffset[1] = findingStart+idtokenValue.length();
                    requestHighlights.add(findingOffset);
                    issues.add(
                        new CustomScanIssue(
                            baseRequestResponse.getHttpService(),
                            helpers.analyzeRequest(baseRequestResponse).getUrl(),
                            new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, requestHighlights, null) },
                            "OpenID Leakage of ID_Token in URL Query",
                            "The request improperly exposes the following OpenID <code>id_token</code> "
                            +"value on its URL query string: <b>"+idtokenValue+"</b>, then a threat agent could be able retrieve it and "
                            +"obtain access to private resources of victim users.",
                            "Medium",
                            "Firm"
                        )
                    );
                }
            }   
        }
        // Additional checks for Authorization Code Leakage issues
        if (!GOTCODES.isEmpty()) {
            String reqReferer = getHttpHeaderValueFromList(reqHeaders, "Referer");
            for (Map.Entry<String,List<String>> entry : GOTCODES.entrySet()) {
                List<String> codeList = entry.getValue();
                for (String codeValue: codeList) {
                    if (reqReferer!=null) {
                        if (reqReferer.contains(codeValue)) {
                            // Found Code Leakage issue on Referer header
                            List<int[]> requestHighlights = new ArrayList<>(1);
                            int findingStart = requestString.indexOf(reqReferer);
                            findingOffset[0] = findingStart;
                            findingOffset[1] = findingStart+reqReferer.length();
                            requestHighlights.add(findingOffset);
                            issues.add(
                                new CustomScanIssue(
                                    baseRequestResponse.getHttpService(),
                                    helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                    new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, requestHighlights, null) },
                                    "OAUTHv2/OpenID Leakage of Authorization Code on Referer Header",
                                    "The request improperly exposes the following OAUTHv2 authorization code "
                                    +"on its Referer header: <b>"+codeValue+"</b>, then a threat agent could be able retrieve it and "
                                    +"potentially gain access to private resources of victim users.",
                                    "Medium",
                                    "Firm"
                                )
                            );
                        }
                    }
                    if (!reqQueryString.isEmpty() & reqQueryString.contains(codeValue)) {
                        // Found Code Leakage issue in URL query
                        List<int[]> requestHighlights = new ArrayList<>(1);
                        int findingStart = requestString.indexOf(codeValue);
                        findingOffset[0] = findingStart;
                        findingOffset[1] = findingStart+codeValue.length();
                        requestHighlights.add(findingOffset);
                        issues.add(
                            new CustomScanIssue(
                                baseRequestResponse.getHttpService(),
                                helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, requestHighlights, null) },
                                "OAUTHv2/OpenID Leakage of Authorization Code in URL Query",
                                "The request improperly exposes the following OAUTHv2 authorization code "
                                +"value on its URL query string: <b>"+codeValue+"</b>, then a threat agent could be able retrieve it and "
                                +"potentially gain access to private resources of victim users.",
                                "Medium",
                                "Firm"
                            )
                        );
                    }
                }                
            }
        }
    
    return issues;
    }






    // Active Scan section ///////////////////////////////

    public List<IScanIssue> redirectScan(IHttpRequestResponse baseRequestResponse, IScannerInsertionPoint insertionPoint) {
        // Scan for open redirect issues on 'redirect_uri' parameter 
        List<IScanIssue> issues = new ArrayList<>();
        Boolean hostheaderCheck = false;
        IHttpRequestResponse checkRequestResponse;
        int[] payloadOffset = new int[2];
        String checkRequestStr;
        IRequestInfo reqInfo = helpers.analyzeRequest(baseRequestResponse);
        URL url = reqInfo.getUrl();
        String proto = url.getProtocol();
        String host = url.getHost();
        byte[] rawrequest = baseRequestResponse.getRequest();
        //String requestStr = helpers.bytesToString(rawrequest);
        IParameter redirectUriParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "redirect_uri");
        IParameter scopeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "scope");
        IParameter clientIdParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "client_id");
        IParameter resptypeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "response_type");
        // Checking only OAUTHv2 and OpenID authorization-code requests
        if (clientIdParameter!=null & resptypeParameter!=null) {
            if (insertionPoint.getInsertionPointName().equals("response_type")) {   // Forcing to perform only a tentative (unique insertion point)
                stdout.println("[+] Active Scan: Checking for Open Redirect on Authorization Code Flow");
                // Iterating for each redirect_uri payload
                for (String payload_redir : INJ_REDIR) {          
                    String redir_match = payload_redir;
                    String originalRedirUri = "";
                    String randomValue = randomString(10);
                    if (payload_redir.contains("SUBDOMAINCHANGEME")) {
                        payload_redir = payload_redir.replace("SUBDOMAINCHANGEME", randomValue); 
                    }
                    // Extracting the original 'redirect_uri' parameter
                    if (redirectUriParameter != null) {
                        originalRedirUri = redirectUriParameter.getValue();
                    } else {
                        originalRedirUri = proto + "://" + host;
                    }
                    hostheaderCheck = false;
                    // Building some specific payloads
                    if (payload_redir.contains("../")) {
                        redir_match = originalRedirUri + payload_redir;
                        payload_redir = originalRedirUri + payload_redir;
                    } else if (payload_redir.contains("..;/")) {
                        redir_match = originalRedirUri + payload_redir;
                        payload_redir = originalRedirUri + payload_redir; 
                    } else if (payload_redir.contains("%2e%2e%2f")) {
                        redir_match = originalRedirUri + payload_redir;
                        payload_redir = originalRedirUri + payload_redir;
                    } else if (payload_redir.contains("#")) {
                        redir_match = payload_redir.replace("#", "");
                        payload_redir = payload_redir + originalRedirUri;  
                    } else if (payload_redir.contains(":password@")) {
                        redir_match = originalRedirUri + payload_redir;
                        payload_redir = originalRedirUri + payload_redir;
                    } else if (payload_redir.contains("&")) {
                        // This payload has the format "&redierct_uri=XYZ" to check multiple 'redirect_uri' issues
                        redir_match = payload_redir.replace("&", "");
                        payload_redir = originalRedirUri + payload_redir;
                    } else if (payload_redir.equals("HOST_HEADER")) {
                        // Change Host header of original request 
                        //in order to check the issue reported on "https://portswigger.net/daily-swig/oauth-standard-exploited-for-account-takeover"
                        hostheaderCheck = true;
                        String newHostname = randomValue + ".burpcollaborator.net";
                        payload_redir = newHostname+"/"+host;
                        redir_match = "https://" + newHostname + "/" + host;
                    } else if (payload_redir.startsWith(".") || payload_redir.startsWith("@")) {
                        redir_match = originalRedirUri + payload_redir;
                        payload_redir = originalRedirUri + payload_redir;
                    } 
                    // Build request containing the payload in the insertion point
                    if (hostheaderCheck) {
                        // Build request with a payload injected on Host header
                        List<String> reqHeaders = reqInfo.getHeaders();
                        List<String> checkReqHeaders = reqHeaders;
                        Boolean isHost = false;
                        String newHeader = "Host: "+ payload_redir;
                        for(String headerItem:checkReqHeaders) {
                            if (headerItem.startsWith("Host: ")) {
                                isHost = true;
                                checkReqHeaders.set(checkReqHeaders.indexOf(headerItem), newHeader);
                            }
                        }
                        if (!isHost) {
                            checkReqHeaders.add(newHeader);
                        }
                        String reqBodyStr = new String(Arrays.copyOfRange(rawrequest, reqInfo.getBodyOffset(), rawrequest.length));
                        byte[] checkRequest = helpers.buildHttpMessage(checkReqHeaders, reqBodyStr.getBytes());
                        checkRequestResponse = callbacks.makeHttpRequest(baseRequestResponse.getHttpService(), checkRequest);
                        checkRequestStr = helpers.bytesToString(checkRequest);
                    } else {
                        if (redirectUriParameter.getType()==IParameter.PARAM_BODY) {
                            IParameter newParam = helpers.buildParameter("redirect_uri", payload_redir, IParameter.PARAM_BODY);
                            byte [] checkRequest = helpers.updateParameter(rawrequest, newParam);
                            checkRequestResponse = callbacks.makeHttpRequest(baseRequestResponse.getHttpService(), checkRequest);
                            checkRequestStr = helpers.bytesToString(checkRequest);
                        } else if (redirectUriParameter.getType()==IParameter.PARAM_URL) {
                            IParameter newParam = helpers.buildParameter("redirect_uri", payload_redir, IParameter.PARAM_URL);
                            byte [] checkRequest = helpers.updateParameter(rawrequest, newParam);
                            checkRequestResponse = callbacks.makeHttpRequest(baseRequestResponse.getHttpService(), checkRequest);
                            checkRequestStr = helpers.bytesToString(checkRequest);
                        } else {
                            // For some custom OAUTH/OpenID request without 'redirect_uri' parameter
                            if (reqInfo.getMethod().equals("POST")) {
                                IParameter newParam = helpers.buildParameter("redirect_uri", payload_redir, IParameter.PARAM_BODY);
                                byte [] checkRequest = helpers.addParameter(rawrequest, newParam);
                                checkRequestResponse = callbacks.makeHttpRequest(baseRequestResponse.getHttpService(), checkRequest);
                                checkRequestStr = helpers.bytesToString(checkRequest);
                            } else {
                                IParameter newParam = helpers.buildParameter("redirect_uri", payload_redir, IParameter.PARAM_URL);
                                byte [] checkRequest = helpers.addParameter(rawrequest, newParam);
                                checkRequestResponse = callbacks.makeHttpRequest(baseRequestResponse.getHttpService(), checkRequest);
                                checkRequestStr = helpers.bytesToString(checkRequest);
                            }
                        }
                    }
                    byte[] checkResponse = checkRequestResponse.getResponse();
                    String checkResponseStr = helpers.bytesToString(checkResponse);
                    List<int[]> activeScanMatches = getMatches(checkResponse, redir_match.getBytes());
                    // Check if open redirect vulnerability is present on authorization request and report the issue
                    if ( (activeScanMatches.size() > 0) & (!checkResponseStr.contains("error") & !checkResponseStr.contains("redirect_uri_mismatch")) ) {
                        List<int[]> requestHighlights = new ArrayList<>(1);
                        int payloadStart = checkRequestStr.indexOf(payload_redir);
                        payloadOffset[0] = payloadStart;
                        payloadOffset[1] = payloadStart+payload_redir.length();
                        requestHighlights.add(payloadOffset);
                        if (hostheaderCheck) {
                            if (scopeParameter!=null) { 
                                if (scopeParameter.getValue().contains("openid") || helpers.urlDecode(resptypeParameter.getValue()).contains("id_token") || helpers.urlDecode(resptypeParameter.getValue()).equals("code token")) {
                                    issues.add(new CustomScanIssue(
                                        baseRequestResponse.getHttpService(),
                                        helpers.analyzeRequest(baseRequestResponse).getUrl(), 
                                        new IHttpRequestResponse[] { callbacks.applyMarkers(checkRequestResponse, requestHighlights, activeScanMatches) }, 
                                        "OpenID Open Redirect via Host Header",
                                        "Found an input validation issue on OpenID Authorization Code (or Hybrid) Flow  request header <code>Host</code>.\n<br>"
                                        +"The payload injected on request:\n <b>"+ payload_redir +"</b>\nwas returned as redirection endpoint "
                                        +"as 'redirect_uri' on response:\n <b>"+ helpers.bytesToString(redir_match.getBytes())+"</b>\n<br>An attacker could "
                                        +"exploit this vulnerability to steal authorization codes by redirecting victim users "
                                        +"to a external domain under his control (account hijacking).\n"
                                        +"In case of whitelisted domains on <code>Host</code> header it could be yet possible to "
                                        +"steal authorization codes by redirecting victim users to a so-called"
                                        +"\"proxy-page\" of Client-Application.\n Proxy pages could be recognized by any of the "
                                        +"following characteristics: pages affected by some specific vulnerabilities "
                                        +"(as Open Redirect, XSS, HTML injection, etc.), or pages containing "
                                        +"dangerous JavaScript handilng query parameters and URL fragments "
                                        +"(as insecure web messaging scripts, etc.).",
                                        "High",
                                        "Certain"));
                                }
                            } else {
                                issues.add(new CustomScanIssue(
                                    baseRequestResponse.getHttpService(),
                                    helpers.analyzeRequest(baseRequestResponse).getUrl(), 
                                    new IHttpRequestResponse[] { callbacks.applyMarkers(checkRequestResponse, requestHighlights, activeScanMatches) }, 
                                    "OAUTHv2 Open Redirect via Host Header",
                                    "Found an input validation issue on OAUTHv2 Authorization Code Flow request header <code>Host</code>.\n<br>"
                                    +"The payload injected on request:\n <b>"+ payload_redir +"</b>\nwas returned as redirection endpoint "
                                    +"as 'redirect_uri' on response:\n <b>"+ helpers.bytesToString(redir_match.getBytes())+"</b>\n<br>An attacker could "
                                    +"exploit this vulnerability to steal authorization codes by redirecting victim users "
                                    +"to a external domain under his control (account hijacking).\n"
                                    +"In case of whitelisted domains on <code>Host</code> header it could be yet possible to "
                                    +"steal authorization codes by redirecting victim users to a so-called"
                                    +"\"proxy-page\" of Client-Application.\n Proxy pages could be recognized by any of the "
                                    +"following characteristics: pages affected by some specific vulnerabilities "
                                    +"(as Open Redirect, XSS, HTML injection, etc.), or pages containing "
                                    +"dangerous JavaScript handilng query parameters and URL fragments "
                                    +"(as insecure web messaging scripts, etc.).\n<br>"
                                    +"<br>References:<br>"
                                    +"<a href=\"https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics-09#section-3.9\">https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics-09#section-3.9</a>",
                                    "High",
                                    "Certain"));
                            }
                        } else {
                            if (scopeParameter!=null) {
                                if (scopeParameter.getValue().contains("openid") || helpers.urlDecode(resptypeParameter.getValue()).contains("id_token") || helpers.urlDecode(resptypeParameter.getValue()).equals("code token")) {
                                    issues.add(new CustomScanIssue(
                                        baseRequestResponse.getHttpService(),
                                        helpers.analyzeRequest(baseRequestResponse).getUrl(), 
                                        new IHttpRequestResponse[] { callbacks.applyMarkers(checkRequestResponse, requestHighlights, activeScanMatches) }, 
                                        "OpenID Open Redirect via Redirect_Uri Parameter",
                                        "Found an input validation issue on OpenID Authorization Code (or Hybrid) Flow request parameter <code>redirect_uri</code>.\n<br>"
                                        +"The payload injected on request:\n <b>"+ payload_redir +"</b>\nwas returned as redirection endpoint " 
                                        +"in response:\n <b>"+ helpers.bytesToString(redir_match.getBytes())+"</b>\n<br>An attacker could "
                                        +"exploit this vulnerability to steal authorization codes by redirecting victim users "
                                        +"to a external domain under his control (account hijacking).\n"
                                        +"In case of whitelisted domains on <code>redirect_uri</code> it could be yet possible to "
                                        +"steal authorization codes by redirecting victim users to a so-called"
                                        +"\"proxy-page\" of Client-Application.\n Proxy pages could be recognized by any of the "
                                        +"following characteristics: pages affected by some specific vulnerabilities "
                                        +"(as Open Redirect, XSS, HTML injection, etc.), or pages containing "
                                        +"dangerous JavaScript handilng query parameters and URL fragments "
                                        +"(as insecure web messaging scripts, etc.).\n<br>"
                                        +"<br>References:<br>"
                                        +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest\">https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest</a>",
                                        "High",
                                        "Certain"));
                                }
                            } else {
                                issues.add(new CustomScanIssue(
                                    baseRequestResponse.getHttpService(),
                                    helpers.analyzeRequest(baseRequestResponse).getUrl(), 
                                    new IHttpRequestResponse[] { callbacks.applyMarkers(checkRequestResponse, requestHighlights, activeScanMatches) }, 
                                    "OAUTHv2 Open Redirect via Redirect_Uri Parameter",
                                    "Found an input validation issue on OAUTHv2 Authorization Code Flow request parameter <code>redirect_uri</code>.\n<br>"
                                    +"The payload injected on request:\n <b>"+ payload_redir +"</b>\nwas returned as redirection endpoint " 
                                    +"in response:\n <b>"+ helpers.bytesToString(redir_match.getBytes())+"</b>\n<br>An attacker could "
                                    +"exploit this vulnerability to steal authorization codes by redirecting victim users "
                                    +"to a external domain under his control (account hijacking).\n"
                                    +"In case of whitelisted domains on <code>redirect_uri</code> it could be yet possible to "
                                    +"steal authorization codes by redirecting victim users to a so-called"
                                    +"\"proxy-page\" of Client-Application.\n Proxy pages could be recognized by any of the "
                                    +"following characteristics: pages affected by some specific vulnerabilities "
                                    +"(as Open Redirect, XSS, HTML injection, etc.), or pages containing "
                                    +"dangerous JavaScript handilng query parameters and URL fragments "
                                    +"(as insecure web messaging scripts, etc.).\n<br>"
                                    +"<br>References:<br>"
                                    +"<a href=\"https://datatracker.ietf.org/doc/html/rfc6749#section-4.4.3\">https://datatracker.ietf.org/doc/html/rfc6749#section-4.4.3</a>",
                                    "High",
                                    "Certain"));
                            }
                        }
                    }
                }
            }
        }
        return issues;
    }


           
    
    public List<IScanIssue> scopeScan(IHttpRequestResponse baseRequestResponse, IScannerInsertionPoint insertionPoint) throws MalformedURLException, UnsupportedEncodingException { 
        // Scan for improper input validation issues of 'scope' parameter in token requests
        List<IScanIssue> issues = new ArrayList<>();
        IHttpRequestResponse checkRequestResponse_code;
        IHttpRequestResponse checkRequestResponse_token;
        IHttpRequestResponse get_checkRequestResponse_token;
        String checkResponseStr_token;
        int[] payloadOffset = new int[2];
        String checkRequestStr;
        Boolean isOpenID = false;
        String locationValue = "";
        IParameter scopeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "scope");
        IParameter resptypeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "response_type");
        IParameter redirectUriParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "redirect_uri");
        IParameter clientIdParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "client_id");
        // This check consists in re-sending a new OAUTHv2/OpenID authorization code request to obtain a new code then add the malicious 'scope' value to the token request
        if (clientIdParameter!=null & resptypeParameter!=null) {
            if (helpers.urlDecode(resptypeParameter.getValue()).contains("id_token") || helpers.urlDecode(resptypeParameter.getValue()).contains("code")) {
                if (insertionPoint.getInsertionPointName().equals("response_type")) {   // Forcing to perform only a tentative (unique insertion point)
                    stdout.println("[+] Active Scan: Checking for Input Validation Issues on Scope parameter in token requests");
                    // Iterating for each scope payload
                    for (String payload_scope : INJ_SCOPE) { 
                        // First re-send the authorization code request to retrieve a new code
                        byte[] checkRequest_code = baseRequestResponse.getRequest();
                        checkRequestResponse_code = callbacks.makeHttpRequest(baseRequestResponse.getHttpService(), checkRequest_code);
                        checkRequestStr = helpers.bytesToString(checkRequest_code);
                        byte [] checkResponse_code = checkRequestResponse_code.getResponse();
                        IResponseInfo checkRespInfo_code = helpers.analyzeResponse(checkResponse_code);
                        //IParameter codeParameter = helpers.getRequestParameter(checkRequestResponse_code.getRequest(), "code");
                        IParameter authorizationParameter = helpers.getRequestParameter(checkRequestResponse_code.getRequest(), "Authorization");
                        String checkResponseStr_code = helpers.bytesToString(checkResponse_code);
                        // For OpenID the code requests could be sent in Authorization Code or Hybrid Flows
                        if (helpers.urlDecode(resptypeParameter.getValue()).contains("id_token") || helpers.urlDecode(resptypeParameter.getValue()).equals("code token")) {
                                // Detected an OpenID Flow
                                isOpenID = true;
                        } else if (scopeParameter!=null) {
                            if (scopeParameter.getValue().contains("openid")) {
                                // Detected an OpenID Flow
                                isOpenID = true;
                            }
                        }
                        List<String> checkRespHeaders_code = checkRespInfo_code.getHeaders();
                        // Then try to retrieve redirection URL from response in order to build a new token request
                        if (checkResponseStr_code.contains("location.href")) {
                            // Redirection via Javascript location.href
                            Pattern pattern = Pattern.compile("location\\.href[\\s=]*['\"]{1}(https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#\\=]{1,256}\\.[a-zA-Z0-9\\(\\)]{1,6}\b([-a-zA-Z0-9\\(\\)@:%_\\+.~#?&//\\=]*))");
                            Matcher matcher = pattern.matcher(checkResponseStr_code);
                            if (matcher.find()) {
                                locationValue = matcher.group(1);
                            }
                        } else if (checkResponseStr_code.contains("location.replace") || checkResponseStr_code.contains("location.assign")) {
                            // Redirection via Javascript location.replace or location.assign
                            Pattern pattern = Pattern.compile("location\\.(assign|replace)[\\('\"]+(https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#\\=]{1,256}\\.[a-zA-Z0-9\\(\\)]{1,6}\b([-a-zA-Z0-9\\(\\)@:%_\\+.~#?&//\\=]*))");
                            Matcher matcher = pattern.matcher(checkResponseStr_code);
                            if (matcher.find()) {
                                locationValue = matcher.group(2);
                            }
                        } else if (checkRespInfo_code.getStatusCode() == 302 & getHttpHeaderValueFromList(checkRespHeaders_code, "Location")!=null) {
                            // Redirection via Location header
                            locationValue = getHttpHeaderValueFromList(checkRespHeaders_code, "Location");
                        } else {
                            // Redirection via HTML tag "<a href" or "<meta http-equiv="refresh"
                            Pattern pattern = Pattern.compile("(?=href|url)[\\s=]*['\"]?(https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#\\=]{1,256}\\.[a-zA-Z0-9\\(\\)]{1,6}\b([-a-zA-Z0-9\\(\\)@:%_\\+.~#?&//\\=]*))");
                            Matcher matcher = pattern.matcher(checkResponseStr_code);
                            if (matcher.find()) {
                                locationValue = matcher.group(2);
                            }
                        }
                        // Start assembling the token request with payload scope parameter
                        if (locationValue.isEmpty()) {
                            // Something goes wrong the redirection url was not found on authorization code response
                            return issues;
                        }
                        // Extract the redirection hostname and path
                        URL url_token = new URL(locationValue);
                        String hostname = url_token.getHost();
                        String path = url_token.getPath();
                        List<String> reqHeaders_token = Arrays.asList("Host: "+hostname);
                        if (authorizationParameter!=null) {
                            reqHeaders_token.add("Authorization: "+authorizationParameter.getValue());
                        }
                        if (scopeParameter!=null) {
                            // If scope parameter was present then add it with the scope payload
                            payload_scope = scopeParameter.getValue()+"%20"+payload_scope;
                        } else {
                            if (isOpenID) {
                                payload_scope = "openid%20"+payload_scope;
                            }
                        }
                        List<String> codeValues =  new ArrayList<>();
                        String checkResponseBody_code = checkResponseStr_code.substring(checkRespInfo_code.getBodyOffset()).trim();
                        // Retrieve the OAUTHv2/OpenID authorizaton code returned by HTTP response
                        for (String pName : SECRETCODES) {
                            codeValues.addAll(getMatchingParams(pName, pName, checkResponseBody_code, getHttpHeaderValueFromList(checkRespHeaders_code, "Content-Type")));
                            codeValues.addAll(getMatchingParams(pName, pName, getHttpHeaderValueFromList(checkRespHeaders_code, "Location"), "header"));
                            codeValues.addAll(getMatchingParams(pName, pName, checkResponseBody_code, "link"));
                        }
                        if (codeValues.isEmpty()) {
                            // Exiting no code was returned in response then the check for scope vulnerability fails
                            return issues;
                        } else {
                            // Found at least an authorization code then remove any duplicate code
                            codeValues = new ArrayList<>(new HashSet<>(codeValues));
                        }
                        for (String codeVal : codeValues) {
                            // Build an HTTP POST token request with each found authorization code value
                            String reqBodyStr_token = "grant_type=authorization_code&code="+codeVal+"&redirect_uri="+redirectUriParameter.getValue()+"&scope="+payload_scope;
                            byte[] reqBody_token = helpers.stringToBytes(reqBodyStr_token);
                            byte[] checkRequest_token = helpers.buildHttpMessage(reqHeaders_token, reqBody_token);
                            String checkRequestStr_token = helpers.bytesToString(checkRequest_token);
                            // Change the path to the exchange code/token url
                            String reqHeadingStr = "POST "+path+" HTTP/1.1\n";
                            checkRequestStr_token = reqHeadingStr + checkRequestStr_token;
                            checkRequest_token = helpers.stringToBytes(checkRequestStr_token);
                            checkRequestResponse_token = callbacks.makeHttpRequest(checkRequestResponse_code.getHttpService(), checkRequest_token);
                            byte[] checkResponse_token = checkRequestResponse_token.getResponse();
                            IResponseInfo checkRespInfo_token = helpers.analyzeResponse(checkResponse_token);
                            checkResponseStr_token = helpers.bytesToString(checkResponse_token);
                            if (checkRespInfo_token.getStatusCode()!=200) {
                                // If HTTP POST method in token request fails, then try using HTTP GET method
                                byte[] get_checkRequest_token = helpers.buildHttpRequest(url_token);
                                String get_checkRequestStr_token = helpers.bytesToString(get_checkRequest_token);
                                get_checkRequest_token = helpers.stringToBytes(get_checkRequestStr_token);
                                get_checkRequestResponse_token = callbacks.makeHttpRequest(checkRequestResponse_code.getHttpService(), get_checkRequest_token);
                                byte[] get_checkResponse_token = get_checkRequestResponse_token.getResponse();
                                checkResponseStr_token = helpers.bytesToString(get_checkResponse_token);
                            }
                            // Search for access token or session cookie returned in response to the replayed code request
                            List<int[]> activeScanMatches = getMatches(checkResponse_token, "token".getBytes());
                            activeScanMatches.addAll(getMatches(checkResponse_token, "Set-Cookie: ".getBytes()));
                            // Check for scope manipulation vulnerability on token requests and report the issue
                            if ( (checkRespInfo_token.getStatusCode()==200) & (!checkResponseStr_token.toLowerCase().contains("error"))) {
                                List<int[]> requestHighlights = new ArrayList<>(1);
                                int payloadStart = checkRequestStr.indexOf(payload_scope);
                                payloadOffset[0] = payloadStart;
                                payloadOffset[1] = payloadStart+payload_scope.length();
                                requestHighlights.add(payloadOffset);
                                if (isOpenID) {
                                    issues.add(new CustomScanIssue(
                                        baseRequestResponse.getHttpService(),
                                        helpers.analyzeRequest(baseRequestResponse).getUrl(), 
                                        new IHttpRequestResponse[] {checkRequestResponse_code, callbacks.applyMarkers(checkRequestResponse_token, requestHighlights, activeScanMatches)},
                                        "OpenID Improper Validation of Scope Parameter",
                                        "The OpenID Flow seems not properly validating the <code>scope</code> request parameter.\n<br>"
                                        +"The Authorization Server accepted the <code>scope</code> parameter value injected on request "
                                        +"<b>"+ payload_scope +"</b> and released a secret token on response.\n<br>"
                                        +"The <code>scope</code> parameter plays an important role during login procedure, because it "
                                        +"defines the users approved permissions for Client-Application during OPenID Flows, note also "
                                        +"that each provider can define custom <code>scope</code> values.\n<br>"
                                        +"A malicious Client-Application abusing this vulnerability could manipulate the <code>scope</code> "
                                        +"parameter of exchange code/token requests, and upgrade the scope of access tokens in order to obtain "
                                        +"some extra permissions in accessing reserved data of victim users.\n<br>"
                                        +"<br>References:<br>"
                                        +"<a href=\"https://openid.net/specs/openid-connect-basic-1_0.html#Scopes\">https://openid.net/specs/openid-connect-basic-1_0.html#Scopes</a>",
                                        "High",
                                        "Firm"));
                                } else {
                                    issues.add(new CustomScanIssue(
                                        baseRequestResponse.getHttpService(),
                                        helpers.analyzeRequest(baseRequestResponse).getUrl(), 
                                        new IHttpRequestResponse[] {checkRequestResponse_code, callbacks.applyMarkers(checkRequestResponse_token, requestHighlights, activeScanMatches)}, 
                                        "OAUTHv2 Improper Validation of Scope Parameter",
                                        "The OAUTHv2 Flow seems not properly validating the <code>scope</code> request parameter.\n<br>"
                                        +"The Authorization Server accepted the <code>scope</code> parameter value injected on request "
                                        +"<b>"+ payload_scope +"</b> and released a secret token on response.\n<br>"
                                        +"The <code>scope</code> parameter plays an important role during login procedure, because it "
                                        +"defines the users approved permissions for Client-Application during OAUTHv2 Flows, note also "
                                        +"that each provider can define custom <code>scope</code> values.\n<br>"
                                        +"A malicious Client-Application abusing this vulnerability could manipulate the <code>scope</code> "
                                        +"parameter of exchange code/token requests, and upgrade the scope of access tokens in order to obtain "
                                        +"some extra permissions in accessing reserved data of victim users.\n<br>"
                                        +"<br>References:<br>"
                                        +"<a href=\"https://datatracker.ietf.org/doc/html/rfc6749#page-23\">https://datatracker.ietf.org/doc/html/rfc6749#page-23</a>",
                                        "High",
                                        "Firm"));
                                }
                            }
                        }
                    }
                }
            }
        }
        return issues;
    }




    public List<IScanIssue> codereplayScan(IHttpRequestResponse baseRequestResponse, IScannerInsertionPoint insertionPoint) {
        // Scan for authorization code replay issues on token requests for OAUTHv2 and OpenID Authorization Code and Hybrid Flows
        List<IScanIssue> issues = new ArrayList<>();
        int[] payloadOffset = new int[2];
        String checkRequestStr;
        IResponseVariations respVariations = null;
        Boolean respDiffers = false;
        IParameter codeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "code");
        IParameter grantParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "grant_type");
        IParameter clientIdParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "client_id");
        if ((codeParameter!= null & grantParameter!=null & clientIdParameter!=null)) {
            // Checking for authorization code replay issues on token requests of OAUTHv2 and OpenID Authorization Code and Hybrid Flows
            if (grantParameter.getValue().equals("authorization_code")) {
                byte[] originalResponse = baseRequestResponse.getResponse();
                String originalResponseStr = helpers.bytesToString(originalResponse);
                IResponseInfo originalRespInfo = helpers.analyzeResponse(originalResponse);
                if (insertionPoint.getInsertionPointName().equals("code")) {   // Forcing to perform only a tentative (unique insertion point)
                    stdout.println("[+] Active Scan: Checking for Autorization Code Replay attack issues");
                    // Build the request to replay 
                    byte[] checkRequest = baseRequestResponse.getRequest();
                    IHttpRequestResponse checkRequestResponse = callbacks.makeHttpRequest(baseRequestResponse.getHttpService(), checkRequest);
                    checkRequestStr = helpers.bytesToString(checkRequest);
                    byte [] checkResponse = checkRequestResponse.getResponse();
                    String checkResponseStr = helpers.bytesToString(checkResponse);
                    IResponseInfo checkRespInfo = helpers.analyzeResponse(checkResponse);
                    // Checking if the replay response was successful
                    if (checkRespInfo.getStatusCode() == originalRespInfo.getStatusCode()) {
                        respVariations = helpers.analyzeResponseVariations(baseRequestResponse.getResponse(), checkRequestResponse.getResponse());
                        List <String> responseChanges = respVariations.getVariantAttributes();
                        for (String change : responseChanges) {
                            if (change.equals("status_code") || change.equals("page_title")) {
                                respDiffers = true;
                            } else if (change.equals("whole_body_content") || change.equals("limited_body_content")) {
                                // If response body differs but neither contains a error message and also both contains a token or a authorization code then respDiffers remain False
                                if ( (checkResponseStr.toLowerCase().contains("error") & (!originalResponseStr.toLowerCase().contains("error"))) & 
                                (((!checkResponseStr.toLowerCase().contains("code")) & (originalResponseStr.toLowerCase().contains("code"))) || 
                                ((!checkResponseStr.toLowerCase().contains("token")) & (originalResponseStr.toLowerCase().contains("token")))) ) {
                                    respDiffers = true;
                                }
                            } 
                        }
                        if (!respDiffers) {
                            String codeString = codeParameter.getValue();
                            List<int[]> requestHighlights = new ArrayList<>(1);
                            int payloadStart = checkRequestStr.indexOf(codeString);
                            payloadOffset[0] = payloadStart;
                            payloadOffset[1] = payloadStart+codeString.length();
                            requestHighlights.add(payloadOffset);
                            // Found OAUTHv2 or OpenID authorization code replay issue
                            issues.add(new CustomScanIssue(
                                baseRequestResponse.getHttpService(),
                                helpers.analyzeRequest(baseRequestResponse).getUrl(), 
                                new IHttpRequestResponse[] {callbacks.applyMarkers(baseRequestResponse, requestHighlights, null), callbacks.applyMarkers(checkRequestResponse, requestHighlights, null) },
                                "OAUTHv2/OpenID Flow Vulnerable to Authorization Code Replay Attacks",
                                "The Resource Server does not invalidate the <code>code</code> parameter after first use "
                                +"so the implemented OAUTHv2/OpenID Flow (Authorization Code or Hybrid) is vulnerable to authorization code replay attacks.\n<br>"
                                +"It was possible to obtain a new access token (or session cookie) by re-sending the authorization code:\n <b>"+ codeString +"</b>\n<br>"
                                +"An attacker, able to retrieve an used <code>code</code> value of any user, could abuse this "
                                +"vulnerability in order to re-exchange the authorization code with a valid access token (or session cookie) "
                                +"and obtain access to reserved data of the victim user.\n<br>"
                                +"<br>References:<br>"
                                +"<a href=\"https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2\">https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2</a>"
                                +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html#TokenRequestValidation\">https://openid.net/specs/openid-connect-core-1_0.html#TokenRequestValidation</a>",
                                "High",
                                "Certain"));
                        }
                    }
                }
            }
        }
        return issues;
    }




    public List<IScanIssue> nonceScan(IHttpRequestResponse baseRequestResponse, IScannerInsertionPoint insertionPoint) {
        // Scan for nonce duplicate replay and nonce not controlled issues for the requests of all OpenID Flows
        List<IScanIssue> issues = new ArrayList<>();
        int[] payloadOffset = new int[2];
        //String checkRequestStr;
        IResponseVariations respVariations = null;
        Boolean isOpenID = false;
        Boolean respDiffers = false;
        IParameter clientIdParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "client_id");
        IParameter resptypeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "response_type");
        IParameter scopeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "scope");
        IParameter nonceParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "nonce");
        if (clientIdParameter!=null & resptypeParameter!=null & nonceParameter!=null) {
            // Determine if is OpenID Flow
            if (scopeParameter!=null) {
                if (scopeParameter.getValue().contains("openid")) {
                    isOpenID = true;
                }
            } else if (helpers.urlDecode(resptypeParameter.getValue()).contains("id_token") || helpers.urlDecode(resptypeParameter.getValue()).equals("code token")) {
                isOpenID = true;
            }
            if (isOpenID) {
                // Checking only on OpenID Flows because only their authorization requests could be affected
                String nonceValue = nonceParameter.getValue();
                byte[] originalResponse = baseRequestResponse.getResponse();
                byte[] originalRequest = baseRequestResponse.getRequest();
                String originalRequestStr = helpers.bytesToString(originalRequest);
                String originalResponseStr = helpers.bytesToString(originalResponse);
                IResponseInfo originalRespInfo = helpers.analyzeResponse(originalResponse);
                if (insertionPoint.getInsertionPointName().equals("nonce")) {   // Forcing to perform only a tentative (unique insertion point)
                    stdout.println("[+] Active Scan: Checking for Duplicate Nonce values on OpenID requests");
                    // Build the request to replay the nonce value
                    byte[] checkRequest = baseRequestResponse.getRequest();
                    IHttpRequestResponse checkRequestResponse = callbacks.makeHttpRequest(baseRequestResponse.getHttpService(), checkRequest);
                    String checkRequestStr = helpers.bytesToString(checkRequest);
                    byte [] checkResponse = checkRequestResponse.getResponse();
                    String checkResponseStr = helpers.bytesToString(checkResponse);
                    IResponseInfo checkRespInfo = helpers.analyzeResponse(checkResponse);
                    // Checking if the replayed nonce response was successful
                    if (checkRespInfo.getStatusCode() == originalRespInfo.getStatusCode()) {
                        respVariations = helpers.analyzeResponseVariations(baseRequestResponse.getResponse(), checkRequestResponse.getResponse());
                        List <String> responseChanges = respVariations.getVariantAttributes();
                        for (String change : responseChanges) {
                            if (change.equals("status_code") || change.equals("page_title")) {
                                respDiffers = true;
                            } else if (change.equals("whole_body_content") || change.equals("limited_body_content")) {
                                // If response body differs but neither contains a error message and also both contains a token or a authorization code then respDiffers remain False
                                if ( (checkResponseStr.toLowerCase().contains("error") & (!originalResponseStr.toLowerCase().contains("error"))) & 
                                (((!checkResponseStr.toLowerCase().contains("code")) & (originalResponseStr.toLowerCase().contains("code"))) || 
                                ((!checkResponseStr.toLowerCase().contains("token")) & (originalResponseStr.toLowerCase().contains("token")))) ) {
                                    respDiffers = true;
                                }
                            } 
                        }
                        if (!respDiffers) {
                            List<int[]> requestHighlights = new ArrayList<>(1);
                            int payloadStart = checkRequestStr.indexOf(nonceValue);
                            payloadOffset[0] = payloadStart;
                            payloadOffset[1] = payloadStart+nonceValue.length();
                            requestHighlights.add(payloadOffset);
                            // Found OpenID nonce duplicate issue
                            issues.add(
                                new CustomScanIssue(
                                    baseRequestResponse.getHttpService(),
                                    helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                    new IHttpRequestResponse[] {callbacks.applyMarkers(baseRequestResponse, requestHighlights, null), callbacks.applyMarkers(checkRequestResponse, requestHighlights, null) },
                                    "OpenID Duplicate Nonce Parameter Detected",
                                    "The OpenID Authorization Code Flow seems using duplicate values for the <code>nonce</code> parameter "
                                    +"during login procedure.\n<br>"
                                    +"The Authorization Server accepted a request with an already used <code>nonce</code> value\n <b>"+ nonceValue +"</b> "
                                    +"and released a new secret token (or authorization code) on response.\n<br>"
                                    +"Based on OpenID specifications the <code>nonce</code> parameter is used to associate a Client session "
                                    +"with an ID Token, and to mitigate replay attacks.\n<br>"
                                    +"Using constant values for the <code>nonce</code> parameter de-facto disables its anti-replay attacks protection, then "
                                    +"the attack surface of the OpenID service increases.\n<br>"
                                    +"<br>References:<br>"
                                    +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html#NonceNotes\">https://openid.net/specs/openid-connect-core-1_0.html#NonceNotes</a>",
                                    "Low",
                                    "Firm"
                                )
                            );
                        }
                    }
                    // Build the request to remove the nonce value
                    byte[] checkRequest_2 = baseRequestResponse.getRequest();
                    // Removing the nonce from request
                    helpers.removeParameter(checkRequest_2, nonceParameter);
                    respDiffers = false;
                    IHttpRequestResponse checkRequestResponse_2 = callbacks.makeHttpRequest(baseRequestResponse.getHttpService(), checkRequest_2);
                    byte [] checkResponse_2 = checkRequestResponse_2.getResponse();
                    String checkResponseStr_2 = helpers.bytesToString(checkResponse_2);
                    IResponseInfo checkRespInfo_2 = helpers.analyzeResponse(checkResponse_2);
                    // Checking if the request without nonce was accepetd
                    if (checkRespInfo_2.getStatusCode() == originalRespInfo.getStatusCode()) {
                        respVariations = null;
                        respVariations = helpers.analyzeResponseVariations(baseRequestResponse.getResponse(), checkRequestResponse_2.getResponse());
                        List <String> responseChanges_2 = respVariations.getVariantAttributes();
                        for (String change : responseChanges_2) {
                            if (change.equals("status_code") || change.equals("page_title")) {
                                respDiffers = true;
                            } else if (change.equals("whole_body_content") || change.equals("limited_body_content")) {
                                // If response body differs but neither contains a error message and also both contains a token or a authorization code then respDiffers remain False
                                if ( (checkResponseStr_2.toLowerCase().contains("error") & (!originalResponseStr.toLowerCase().contains("error"))) & 
                                (((!checkResponseStr_2.toLowerCase().contains("code")) & (originalResponseStr.toLowerCase().contains("code"))) || 
                                ((!checkResponseStr_2.toLowerCase().contains("token")) & (originalResponseStr.toLowerCase().contains("token")))) ) {
                                    respDiffers = true;
                                }
                            } 
                        }
                        if (!respDiffers) {
                            List<int[]> requestHighlights = new ArrayList<>(1);
                            int payloadStart = originalRequestStr.indexOf(nonceValue);
                            payloadOffset[0] = payloadStart;
                            payloadOffset[1] = payloadStart+nonceValue.length();
                            requestHighlights.add(payloadOffset);
                            // Found OpenID nonce not controlled issue
                            issues.add(
                                new CustomScanIssue(
                                    baseRequestResponse.getHttpService(),
                                    helpers.analyzeRequest(baseRequestResponse).getUrl(),
                                    new IHttpRequestResponse[] {callbacks.applyMarkers(baseRequestResponse, requestHighlights, null), callbacks.applyMarkers(checkRequestResponse, null, null) },
                                    "OpenID Flow Nonce Parameter not Evaluated",
                                    "The OpenID Flow is improperly implemented because the Authorization Server does not validates "
                                    +"the <code>nonce</code> parameter on login requests.\n<br>"
                                    +"In details, the Authorization Server successfully accepted both a request containing the <code>nonce</code> "
                                    +"parameter value " +nonceValue+" and also a request without any <code>nonce</code> parameter."
                                    +"Based on OpenID specifications the <code>nonce</code> parameter should be randomic and unique per client session "
                                    +"in order to provide a security mitigation against replay attacks.\nNot validating the <code>nonce</code> values "
                                    +"de-facto disables its protections and increases the attack surface of the OpenID service.\n<br>"
                                    +"<br>References:<br>"
                                    +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html#NonceNotes\">https://openid.net/specs/openid-connect-core-1_0.html#NonceNotes</a>",
                                    "Low",
                                    "Firm"
                                )
                            );
                        }
                    }
                }
            }
        }
        return issues;
    }




    public List<IScanIssue> resptypeScan(IHttpRequestResponse baseRequestResponse, IScannerInsertionPoint insertionPoint) {
        // Scan for OpenID 'response_type' with none value issues on authorization-code requests
        List<IScanIssue> issues = new ArrayList<>();
        IHttpRequestResponse checkRequestResponse;
        int[] payloadOffset = new int[2];
        String checkRequestStr;
        Boolean isOpenID = false;
        byte[] rawrequest = baseRequestResponse.getRequest();
        IParameter resptypeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "response_type");
        IParameter scopeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "scope");
        IParameter clientIdParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "client_id");
        if (clientIdParameter!=null & resptypeParameter!=null) {
            // Determine if is OpenID Flow
            if (scopeParameter!=null) {
                if (scopeParameter.getValue().contains("openid")) {
                    isOpenID = true;
                }
            } else if (helpers.urlDecode(resptypeParameter.getValue()).contains("id_token") || helpers.urlDecode(resptypeParameter.getValue()).equals("code token")) {
                isOpenID = true;
            }
            // Checking only on OpenID Flows because only their authorization requests could be affected
            if (isOpenID) {
                String payload_resptypenone = "none";
                if (insertionPoint.getInsertionPointName().equals("response_type")) {   // Forcing to perform only a tentative (unique insertion point)
                    stdout.println("[+] Active Scan: Checking for OpenID response_type parameter set to None Value issues");
                    // Build request containing the payload in the 'request_type' parameter
                    if (resptypeParameter.getType()==IParameter.PARAM_BODY) {
                        IParameter newParam = helpers.buildParameter("response_type", payload_resptypenone, IParameter.PARAM_BODY);
                        byte [] checkRequest = helpers.updateParameter(rawrequest, newParam);
                        checkRequestResponse = callbacks.makeHttpRequest(baseRequestResponse.getHttpService(), checkRequest);
                        checkRequestStr = helpers.bytesToString(checkRequest);
                    } else if (resptypeParameter.getType()==IParameter.PARAM_URL) {
                        IParameter newParam = helpers.buildParameter("response_type", payload_resptypenone, IParameter.PARAM_URL);
                        byte [] checkRequest = helpers.updateParameter(rawrequest, newParam);
                        checkRequestResponse = callbacks.makeHttpRequest(baseRequestResponse.getHttpService(), checkRequest);
                        checkRequestStr = helpers.bytesToString(checkRequest);
                    } else {
                        // Discarding malformed requests containing a response_type parameter
                        return issues;
                    }
                    byte [] checkResponse = checkRequestResponse.getResponse();
                    String checkResponseStr = helpers.bytesToString(checkResponse);
                    List<int[]> activeScanMatches = getMatches(checkRequestResponse.getResponse(), "code".getBytes());
                    activeScanMatches.addAll(getMatches(checkRequestResponse.getResponse(), "token".getBytes()));
                    // Check if vulnerable and report the issue
                    if ((activeScanMatches.size() > 0) & (!checkResponseStr.toLowerCase().contains("error"))) {
                        List<int[]> requestHighlights = new ArrayList<>(1);
                        int payloadStart = checkRequestStr.indexOf(payload_resptypenone);
                        payloadOffset[0] = payloadStart;
                        payloadOffset[1] = payloadStart+payload_resptypenone.length();
                        requestHighlights.add(payloadOffset);
                        issues.add(new CustomScanIssue(
                            baseRequestResponse.getHttpService(),
                            helpers.analyzeRequest(baseRequestResponse).getUrl(), 
                            new IHttpRequestResponse[] { callbacks.applyMarkers(checkRequestResponse, requestHighlights, activeScanMatches) }, 
                            "OpenID Misconfiguration - Response Type set to None Accepted",
                            "Found a misconfiguration on OpenID Flow when request parameter <code>response_type</code> value is set to <b>none</b>.\n<br>"
                            +"The Authorization Server does not rejects the requests contaning the parameter:\n <b>"+ payload_resptypenone +"</b>\n, "
                            +"and instead it returns a valid authorization code or access token in response.\n<br>"
                            +"OpenID specifications require that when the <code>response_type</code> parameter is set to <b>none</b> "
                            +"the Authorization Server should never release authorization codes or access tokens to the Client-Application\n<br>"
                            +"<br>References:\n<ul>"
                            +"<li><a href=\"https://openid.net/specs/oauth-v2-multiple-response-types-1_0.html#none\">https://openid.net/specs/oauth-v2-multiple-response-types-1_0.html#none</a></li></ul>",
                            "Low",
                            "Certain"));
                    }
                }
            }          
        }
        return issues;
    }



    public List<IScanIssue> wellknownScan(IHttpRequestResponse baseRequestResponse, IScannerInsertionPoint insertionPoint) throws MalformedURLException {
        // Scan for information exposed on wellKnown urls
        List<IScanIssue> issues = new ArrayList<>();
        String checkRequestStr;
        int[] payloadOffset = new int[2];
        IRequestInfo reqInfo = helpers.analyzeRequest(baseRequestResponse);
        URL url = reqInfo.getUrl();
        String proto = url.getProtocol();
        String host = url.getHost();
        int port = url.getPort();
        String authority = url.getAuthority();
        String origin = url.getProtocol() + "://" + authority;
        Boolean isOpenID = false;
        IParameter scopeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "scope");
        IParameter clientIdParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "client_id");
        IParameter resptypeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "response_type");
        // First check if the system was already checked for well known urls
        if (!alreadyChecked.contains(authority) & (clientIdParameter!=null & resptypeParameter!=null)) {
            alreadyChecked.add(authority);
            List<String> listWithoutDuplicates = new ArrayList<>(new HashSet<>(alreadyChecked));
            alreadyChecked = listWithoutDuplicates;
            stdout.println("[+] Active Scan: Searching for OAUTHv2/OpenID Well-Known urls");
            for (String payload_url : WELL_KNOWN) {
                // Determine if is OpenID Flow
                if (scopeParameter!=null) {
                    if (scopeParameter.getValue().contains("openid")) {
                        isOpenID = true;
                    }
                } else if (helpers.urlDecode(resptypeParameter.getValue()).contains("id_token") || helpers.urlDecode(resptypeParameter.getValue()).equals("code token")) {
                    isOpenID = true;
                }
                // Checking for WebFinger issues
                if (payload_url.contains("resource")) {
                    if (payload_url.contains("ORIGINCHANGEME")) {
                        payload_url = payload_url.replace("ORIGINCHANGEME", origin);
                    } else {
                        payload_url = payload_url.replace("URLCHANGEME", reqInfo.getUrl().getHost());
                    }
                    List<String> usersList = Arrays.asList("admin", "anonymous", "test");
                    for (String username: usersList) {
                        payload_url = payload_url.replace("USERCHANGEME", username);
                        // Build request to check webfinger service 
                        URL welknownUrl = new URL(proto, host, port, payload_url);
                        byte[] checkRequest = helpers.buildHttpRequest(welknownUrl);
                        IHttpRequestResponse checkRequestResponse = callbacks.makeHttpRequest(baseRequestResponse.getHttpService(), checkRequest);
                        checkRequestStr = helpers.bytesToString(checkRequest);
                        byte [] checkResponse = checkRequestResponse.getResponse();
                        IResponseInfo checkRespInfo = helpers.analyzeResponse(checkResponse);
                        // Looking for successful access to webfinger service
                        if (checkRespInfo.getStatusCode()==200) {
                            List<int[]> requestHighlights = new ArrayList<>(1);
                            int payloadStart = checkRequestStr.indexOf(payload_url);
                            payloadOffset[0] = payloadStart;
                            payloadOffset[1] = payloadStart+payload_url.length();
                            requestHighlights.add(payloadOffset);
                            String checkresponseString = helpers.bytesToString(checkResponse);
                            if (checkresponseString.contains("subject") & checkresponseString.contains(username)) {
                                issues.add(new CustomScanIssue(
                                    baseRequestResponse.getHttpService(),
                                    helpers.analyzeRequest(baseRequestResponse).getUrl(), 
                                    new IHttpRequestResponse[] { callbacks.applyMarkers(checkRequestResponse, requestHighlights, null) }, 
                                    "OpenID WebFinger Service Exposed",
                                    "The OpenID webfinger service is publicly exposed on a well known url.\n<br><br>"
                                    +"Care must be taken when exposing the OpenID WebFinger service, because "
                                    +"it could potentially increase the attack surface of the OpenID service, and allow "
                                    +"unauthenticated users to retrieve information about registered accounts and resources\n<br><br>"
                                    +"Querying it revealed that the \""+username+"\" account is enabled on the server.\n<br>"
                                    +"The configuration file was found at URL:\n <b>"+ origin+"/"+payload_url +"</b>\n<br><br>"
                                    +"Note that there are various possible attacks against OpenID WebFinger, for example:<br><ul>"
                                    +"<li>User Enumeration by sending requests as <code>/.well-known/webfinger?resource=http://URL/USERNAME&rel=http://openid.net/specs/connect/1.0/issuer</code>"
                                    +"or <code>/.well-known/webfinger?resource=acct:USERNAME@URL&rel=http://openid.net/specs/connect/1.0/issuer</code></li>"
                                    +"<li>LDAP inj by sending requests as <code>/.well-known/webfinger?resource=http://URL/mar*&rel=http://openid.net/specs/connect/1.0/issuer</code></li>"
                                    +"<li>SQL inj by sending requests as <code>/.well-known/webfinger?resource=http://x/mario'&rel=http://openid.net/specs/connect/1.0/issuer</code></li></ul>"
                                    +"<br>References:\n<ul>"
                                    +"<li><a href=\"https://openid.net/specs/openid-connect-discovery-1_0.html\">https://openid.net/specs/openid-connect-discovery-1_0.html</a></li></ul>"
                                    +"<li><a href=\"https://datatracker.ietf.org/doc/html/rfc7033\">https://datatracker.ietf.org/doc/html/rfc7033</a></li></ul>",
                                    "Information",
                                    "Certain"
                                ));
                            }
                        }
                    }
                } else {
                    // Build the request to check well known urls 
                    URL welknownUrl = new URL(proto, host, port, payload_url);
                    byte[] checkRequest = helpers.buildHttpRequest(welknownUrl);
                    IHttpRequestResponse checkRequestResponse = callbacks.makeHttpRequest(baseRequestResponse.getHttpService(), checkRequest);
                    checkRequestStr = helpers.bytesToString(checkRequest);
                    byte [] checkResponse = checkRequestResponse.getResponse();
                    IResponseInfo checkRespInfo = helpers.analyzeResponse(checkResponse);
                    // Looking for successful access to well known config urls
                    if (checkRespInfo.getStatusCode()==200) {
                        List<int[]> requestHighlights = new ArrayList<>(1);
                        int payloadStart = checkRequestStr.indexOf(payload_url);
                        payloadOffset[0] = payloadStart;
                        payloadOffset[1] = payloadStart+payload_url.length();
                        requestHighlights.add(payloadOffset);
                        if (isOpenID) {
                            // Found well-known url in OpenID Flow 
                            issues.add(new CustomScanIssue(
                                baseRequestResponse.getHttpService(),
                                helpers.analyzeRequest(baseRequestResponse).getUrl(), 
                                new IHttpRequestResponse[] { callbacks.applyMarkers(checkRequestResponse, requestHighlights, null) }, 
                                "OpenID Configuration Files in Well-Known URLs",
                                "Found OpenID configuration file publicly exposed on some well known urls.\n<br>"
                                +"The retrieved JSON configuration file contains some key information, such as details of "
                                +"additional features that may be supported.\n These files will sometimes give hints "
                                +"about a wider attack surface and supported features that may not be mentioned in the documentation.\n<br>"
                                +"The configuration file was found at URL:\n <b>"+ origin+"/"+payload_url +"</b>\n<br>"
                                +"<br>References:\n<ul>"
                                +"<li><a href=\"https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfigurationRequest\">https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfigurationRequest</a></li></ul>",
                                "Information",
                                "Certain"));
                        } else {
                            // Found well-known url in OAUTHv2 Flow 
                            issues.add(new CustomScanIssue(
                                baseRequestResponse.getHttpService(),
                                helpers.analyzeRequest(baseRequestResponse).getUrl(), 
                                new IHttpRequestResponse[] { callbacks.applyMarkers(checkRequestResponse, requestHighlights, null) }, 
                                "OAUTHv2 Configuration Files in Well-Known URLs",
                                "Found OAUTHv2 configuration file publicly exposed on some well known urls.\n<br>"
                                +"The retrieved JSON configuration file contains some key information, such as details of "
                                +"additional features that may be supported.\n These files will sometimes give hints "
                                +"about a wider attack surface and supported features that may not be mentioned in the documentation.\n<br>"
                                +"The configuration file was found at URL:\n <b>"+ origin+"/"+payload_url +"</b>\n<br>"
                                +"<br>References:\n<ul>"
                                +"<li><a href=\"https://tools.ietf.org/id/draft-ietf-oauth-discovery-08.html#:~:text=well%2Dknown%2Foauth%2Dauthorization,will%20use%20for%20this%20purpose.\">https://tools.ietf.org/id/draft-ietf-oauth-discovery-08.html#:~:text=well%2Dknown%2Foauth%2Dauthorization,will%20use%20for%20this%20purpose.</a></li></ul>",
                                "Information",
                                "Certain"));
                        }
                    }
                }
            }
        }         
        return issues;
    }




    public List<IScanIssue> requriScan(IHttpRequestResponse baseRequestResponse, IScannerInsertionPoint insertionPoint) {
        // Scan for authorization code replay issues on token requests for OAUTHv2 and OpenID Authorization Code and Hybrid Flows
        List<IScanIssue> issues = new ArrayList<>();
        int[] payloadOffset = new int[2];
        Boolean isOpenID = false;
        IParameter requriParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "request_uri");
        IParameter scopeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "scope");
        IParameter resptypeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "response_type");
        IParameter clientIdParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "client_id");
        if ((resptypeParameter!=null & clientIdParameter!=null)) {
            // Checking for request_uri SSRF issues on authorization requests of all OAUTHv2 and OpenID Flows
            if (insertionPoint.getInsertionPointName().equals("response_type")) {   // Forcing to perform only a tentative (unique insertion point)
                stdout.println("[+] Active Scan: Checking for Request Uri SSRF issues");
                // Build the request for the check 
                byte[] checkRequest = baseRequestResponse.getRequest();
                // Determine if is OpenID Flow
                if (scopeParameter!=null) {
                    if (scopeParameter.getValue().contains("openid")) {
                        isOpenID = true;
                    }
                } else if (helpers.urlDecode(resptypeParameter.getValue()).contains("id_token") || helpers.urlDecode(resptypeParameter.getValue()).equals("code token")) {
                    isOpenID = true;
                }
                if (requriParameter!=null) {
                    // Remove the 'request_uri' parameter if present on the authorization request 
                    checkRequest = helpers.removeParameter(checkRequest, requriParameter);
                }
                // Adding a malicious 'request_uri' parameter
                byte parameterType = resptypeParameter.getType();
                String randomValue = randomString(10);
                String checkRequriValue = "https://oauthscan"+randomValue+".burpcollaborator.net/requesturi.jwt";
                IParameter checkRequriParameter = helpers.buildParameter("request_uri", checkRequriValue, parameterType);
                checkRequest = helpers.addParameter(checkRequest, checkRequriParameter);
                String checkRequestStr = helpers.bytesToString(checkRequest);
                IHttpRequestResponse checkRequestResponse = callbacks.makeHttpRequest(baseRequestResponse.getHttpService(), checkRequest);
                List<int[]> requestHighlights = new ArrayList<>(1);
                int payloadStart = checkRequestStr.indexOf(checkRequriValue);
                payloadOffset[0] = payloadStart;
                payloadOffset[1] = payloadStart+checkRequriValue.length();
                requestHighlights.add(payloadOffset);
                if (isOpenID) {
                    // Sent the malicious 'request_uri' parameter to OpenID Provider 
                    // and then waiting for burpcollaborator warnings
                    issues.add(new CustomScanIssue(
                    baseRequestResponse.getHttpService(),
                    helpers.analyzeRequest(baseRequestResponse).getUrl(), 
                    new IHttpRequestResponse[] {callbacks.applyMarkers(checkRequestResponse, requestHighlights, null) },
                    "OpenID Flow Checking SSRF via Request_Uri",
                    "A request containing the parameter <code>request_uri</code> set to the value <b>"+checkRequriValue+"</b> was"
                    +"sent to the OpenID Provider in order to verify if it is vulnerable to blind SSRF issues.\n<br>"
                    +"If the OpenID Provider will interacts (via DNS or HTTP) with the remote BurpCollabortor server specified on  "
                    +"the <code>request_uri</code> parameter, then it is confirmed to be vulnerable.\n<br>"
                    +"Based on OpenID specifications the value of the <code>request_uri</code> parameter "
                    +"is set to an URI pointing to a server hosting a JWT which contains the client's parameter values. "
                    +"In this way the OpenID Provider can fetch the provided URI and retrieve the Request-Object "
                    +"by parsing the JWT contents.\n<br>"
                    +"For security reasons the URI value of <code>request_uri</code> parameter should be carefully validated "
                    +"at server-side, otherwise a threat agent could be able to lead the OpenID Provider to interact with "
                    +"an arbitrary server under is control and then potentially exploit SSRF vulnerabilities.\n<br>"
                    +"As mitigation the OpenID Provider should define a whitelist of allowed URI values (pre-registered "
                    +"during the client registration process) for the <code>request_uri</code> parameter.\n<br>"
                    +"<br>References:<br>"
                    +"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.6.2\">https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.6.2</a>"
                    +"<a href=\"https://portswigger.net/research/hidden-oauth-attack-vectors\">https://portswigger.net/research/hidden-oauth-attack-vectors</a>",
                    "High",
                    "Tentative"));
                } else {
                    // Sent the malicious 'request_uri' parameter to OAUTHv2 Provider
                    // and then waiting for burpcollaborator warnings
                    issues.add(new CustomScanIssue(
                    baseRequestResponse.getHttpService(),
                    helpers.analyzeRequest(baseRequestResponse).getUrl(), 
                    new IHttpRequestResponse[] {callbacks.applyMarkers(checkRequestResponse, requestHighlights, null) },
                    "OAUTHv2 Flow Checking SSRF via Request_Uri",
                    "A request containing the parameter <code>request_uri</code> set to the value <b>"+checkRequriValue+"</b> was"
                    +"sent to the OAUTHv2 Provider in order to verify if it is vulnerable to blind SSRF issues.\n<br>"
                    +"If the OAUTHv2 Provider will interacts (via DNS or HTTP) with the remote BurpCollabortor server specified on "
                    +"the <code>request_uri</code> parameter, then it is confirmed to be vulnerable.\n<br>"
                    +"Based on OAUTHv2 specifications the value of the <code>request_uri</code> parameter "
                    +"is set to an URI pointing to a server hosting a JWT which contains the client's parameter values. "
                    +"In this way the OAUTHv2 Provider can fetch the provided URI and retrieve the Request-Object "
                    +"by parsing the JWT contents.\n<br>"
                    +"For security reasons the URI value of <code>request_uri</code> parameter should be carefully validated "
                    +"at server-side, otherwise a threat agent could be able to lead the OAUTHv2 Provider to interact with "
                    +"an arbitrary server under is control and then potentially exploit SSRF vulnerabilities.\n<br>"
                    +"As mitigation the OAUTHv2 Provider should define a whitelist of allowed URI values (pre-registered "
                    +"during the client registration process) for the <code>request_uri</code> parameter.\n<br>"
                    +"<br>References:<br>"
                    +"<a href=\"https://tools.ietf.org/html/draft-lodderstedt-oauth-par\">https://tools.ietf.org/html/draft-lodderstedt-oauth-par</a>"
                    +"<a href=\"https://portswigger.net/research/hidden-oauth-attack-vectors\">https://portswigger.net/research/hidden-oauth-attack-vectors</a>",
                    "High",
                    "Tentative"));
                }
            }
        }
        return issues;
    }








    public List<IScanIssue> pkceScan(IHttpRequestResponse baseRequestResponse, IScannerInsertionPoint insertionPoint) {
        // Scan for PKCE downgrade issues on authorization requests for OAUTHv2 and OpenID Authorization Code and Hybrid Flows
        List<IScanIssue> issues = new ArrayList<>();
        int[] payloadOffset = new int[2];
        Boolean isOpenID = false;
        IResponseVariations respVariations = null;
        Boolean respDiffers = false;
        IParameter challengemethodParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "code_challenge_method");
        IParameter codechallengeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "code_challenge");
        IParameter scopeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "scope");
        IParameter resptypeParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "response_type");
        IParameter clientIdParameter = helpers.getRequestParameter(baseRequestResponse.getRequest(), "client_id");
        if ((resptypeParameter!=null & clientIdParameter!=null & codechallengeParameter!=null & challengemethodParameter!=null) ) {
            if (resptypeParameter.getValue().contains("code")) {
                // Checking for request_uri SSRF issues on authorization requests of all OAUTHv2 and OpenID Flows
                byte[] originalResponse = baseRequestResponse.getResponse();
                String originalResponseStr = helpers.bytesToString(originalResponse);
                IResponseInfo originalRespInfo = helpers.analyzeResponse(originalResponse);
                if (insertionPoint.getInsertionPointName().equals("code_challenge_method")) {   // Forcing to perform only a tentative (unique insertion point)
                    stdout.println("[+] Active Scan: Checking for PKCE Downgrade issues");
                    // Build the request for the check 
                    byte[] checkRequest = baseRequestResponse.getRequest();
                    // Determine if is OpenID Flow
                    if (scopeParameter!=null) {
                        if (scopeParameter.getValue().contains("openid")) {
                            isOpenID = true;
                        }
                    } else if ( helpers.urlDecode(resptypeParameter.getValue()).equals("code token") || resptypeParameter.getValue().contains("id_token")) {
                        isOpenID = true;
                    }
                    if (!challengemethodParameter.getValue().toLowerCase().equals("plain")) {
                        // Check not necessary PKCE is already set to plaintext
                        return issues;
                    }
                    // Removing the 'code_challenge' parameter from the authorization request to check the downgrade issue
                    checkRequest = helpers.removeParameter(checkRequest, codechallengeParameter);
                    String codechallengeValue = codechallengeParameter.getValue();
                    String challengemethodValue = challengemethodParameter.getValue();
                    String checkRequestStr = helpers.bytesToString(checkRequest);
                    IHttpRequestResponse checkRequestResponse = callbacks.makeHttpRequest(baseRequestResponse.getHttpService(), checkRequest);
                    byte [] checkResponse = checkRequestResponse.getResponse();
                    IResponseInfo checkRespInfo = helpers.analyzeResponse(checkResponse);
                    String checkResponseStr = helpers.bytesToString(checkResponse);
                    // Checking if the downgraded PKCE response was successful
                    if (checkRespInfo.getStatusCode() == originalRespInfo.getStatusCode()) {
                        respVariations = helpers.analyzeResponseVariations(baseRequestResponse.getResponse(), checkRequestResponse.getResponse());
                        List <String> responseChanges = respVariations.getVariantAttributes();
                        for (String change : responseChanges) {
                            if (change.equals("status_code") || change.equals("page_title")) {
                                respDiffers = true;
                            } else if (change.equals("whole_body_content") || change.equals("limited_body_content")) {
                                // If response body differs but neither contains a error message and also both contains a token or a authorization code then respDiffers remain False
                                if ( (checkResponseStr.toLowerCase().contains("error") & (!originalResponseStr.toLowerCase().contains("error"))) & 
                                (((!checkResponseStr.toLowerCase().contains("code")) & (originalResponseStr.toLowerCase().contains("code"))) || 
                                ((!checkResponseStr.toLowerCase().contains("token")) & (originalResponseStr.toLowerCase().contains("token")))) ) {
                                    respDiffers = true;
                                }
                            } 
                        }
                        if (!respDiffers) {
                            List<int[]> requestHighlights = new ArrayList<>(1);
                            int payloadStart = checkRequestStr.indexOf(codechallengeValue);
                            payloadOffset[0] = payloadStart;
                            payloadOffset[1] = payloadStart+codechallengeValue.length();
                            requestHighlights.add(payloadOffset);
                            if (isOpenID) {
                                // Successful downgraded OpenID PKCE to plaintext
                                issues.add(new CustomScanIssue(
                                    baseRequestResponse.getHttpService(),
                                    helpers.analyzeRequest(baseRequestResponse).getUrl(), 
                                    new IHttpRequestResponse[] {callbacks.applyMarkers(baseRequestResponse, requestHighlights, null), callbacks.applyMarkers(checkRequestResponse, null, null) },
                                    "OpenID Flow PKCE Downgraded to Plaintext",
                                    "The OpenID Flow results vulnerable to PKCE Downgrade attacks.\n<br><br>"
                                    +"When OpenID Flows supports PKCE but does not make its use mandatory, the Authorization Server accepts authorization requests "
                                    +"without the PKCE <code>code_challenge</code> parameter and returns a valid authorization <code>code</code> in response, "
                                    +"because it assumes that the <b>plain</b> challenge method is in use "
                                    +"(this means <code>code_challenge</code> = <code>code_verifier</code>).\n<br><br> "
                                    +"In details, the Authorization Server accepted successfully both an authorization request having the PKCE parameter "
                                    +"<code>code_challenge</code> set to the value <b>"+codechallengeValue+"</b> (and the parameter <code>challenge_method_value</code> "
                                    +"set to <b>"+challengemethodValue+"</b>), and also a request without it.\n<br><br>"
                                    +"A threat agent could exploit this issue in order to defeat the PKCE protections against authorization code "
                                    +"interception attacks.\n<br>"
                                    +"In Mobile, Native desktop and SPA contexts is a security requirement to use OpenID Authorization Code Flow with PKCE extension "
                                    +"or alternatively to use OpenID Hybrid Flow.\n<br>"
                                    +"<br>References:<br>"
                                    +"<a href=\"https://openid.net/specs/openid-igov-oauth2-1_0-02.html#rfc.section.3.1.7\">https://openid.net/specs/openid-igov-oauth2-1_0-02.html#rfc.section.3.1.7</a>",
                                    "Medium",
                                    "Certain"));
                            } else {
                                // Successful downgraded OAUTHv2 PKCE to plaintext
                                issues.add(new CustomScanIssue(
                                    baseRequestResponse.getHttpService(),
                                    helpers.analyzeRequest(baseRequestResponse).getUrl(), 
                                    new IHttpRequestResponse[] {callbacks.applyMarkers(baseRequestResponse, requestHighlights, null), callbacks.applyMarkers(checkRequestResponse, null, null) },
                                    "OAUTHv2 Flow PKCE Downgraded to Plaintext",
                                    "The OAUTHv2 Flow results vulnerable to PKCE Downgrade attacks.\n<br><br>"
                                    +"When OAUTHv2 Flows supports PKCE but does not make its use mandatory, the Authorization Server accepts authorization requests "
                                    +"without the PKCE <code>code_challenge</code> parameter and returns a valid authorization <code>code</code> in response, "
                                    +"because it assumes that the <b>plain</b> challenge method is in use "
                                    +"(this means <code>code_challenge</code> = <code>code_verifier</code>).\n<br><br> "
                                    +"In details, the Authorization Server accepted successfully both an authorization request having the PKCE parameter "
                                    +"<code>code_challenge</code> set to the value <b>"+codechallengeValue+"</b> (and the parameter <code>challenge_method_value</code> "
                                    +"set to <b>"+challengemethodValue+"</b>), and also a request without it.\n<br><br>"
                                    +"A threat agent could exploit this issue in order to defeat the PKCE protections against authorization code "
                                    +"interception attacks.\n<br>"
                                    +"In Mobile, Native desktop and SPA contexts is a security requirement to use OAUTHv2 Authorization Code Flow with PKCE extension.\n<br>"
                                    +"<br>References:<br>"
                                    +"<a href=\"https://datatracker.ietf.org/doc/html/rfc7636\">https://datatracker.ietf.org/doc/html/rfc7636</a>",
                                    "Medium",
                                    "Certain"));
                            }
                        }
                    }
                }
            }
        }
        return issues;
    }



    @Override
    public List<IScanIssue> doActiveScan(IHttpRequestResponse baseRequestResponse, IScannerInsertionPoint insertionPoint) {
        List<IScanIssue> issues = new ArrayList<>();
        // Start Active Scan for each OAUTHv2/OpenID vulnerability and common misconfiguration
        try {
            List<IScanIssue> redirResults = redirectScan(baseRequestResponse, insertionPoint);
            List<IScanIssue> scopeResults = scopeScan(baseRequestResponse, insertionPoint);
            List<IScanIssue> codereplayResults = codereplayScan(baseRequestResponse, insertionPoint);
            List<IScanIssue> nonceResults = nonceScan(baseRequestResponse, insertionPoint);
            List<IScanIssue> resptypeResults = resptypeScan(baseRequestResponse, insertionPoint);
            List<IScanIssue> wellknownResults = wellknownScan(baseRequestResponse, insertionPoint);
            List<IScanIssue> requriResults = requriScan(baseRequestResponse, insertionPoint);
            List<IScanIssue> pkceResults = pkceScan(baseRequestResponse, insertionPoint);
            issues.addAll(redirResults);
            issues.addAll(scopeResults);
            issues.addAll(codereplayResults);
            issues.addAll(nonceResults);
            issues.addAll(resptypeResults);
            issues.addAll(wellknownResults);
            issues.addAll(requriResults);
            issues.addAll(pkceResults);
        } catch (Exception exc) {
            exc.printStackTrace(stderr);
        }
        return issues;
    }




    @Override
    public int consolidateDuplicateIssues(IScanIssue existingIssue, IScanIssue newIssue) {
        if (existingIssue.getIssueName().equals(newIssue.getIssueName())) {
            if (existingIssue.getHttpMessages().equals(newIssue.getHttpMessages())) {
                return -1;
            }
        }
        return 0;
    }


    @Override
    public void extensionUnloaded() {
        // Unload the plugin
        stdout.println("[+] OAUTHscan Plugin Unloaded");
    }


    @Override
    public List<IScannerInsertionPoint> getInsertionPoints(IHttpRequestResponse baseRequestResponse) {
        return null;
    }



}




// Class implementing IScanIssue
class CustomScanIssue implements IScanIssue
{
	private IHttpService httpService;
	private URL url;
	private IHttpRequestResponse[] httpMessages;
	private String name;
	private String detail;
	private String severity;
	private String confidence;

	public CustomScanIssue(IHttpService httpService, URL url, IHttpRequestResponse[] httpMessages, String name, String detail, String severity, String confidence)
	{
		this.httpService = httpService;
		this.url = url;
		this.httpMessages = httpMessages;
		this.name = name;
		this.detail = detail;
		this.severity = severity;
		this.confidence = confidence;
	}

	@Override
	public URL getUrl()
	{
		return url;
	}

	@Override
	public String getIssueName()
	{
		return name;
	}

	@Override
	public int getIssueType()
	{
		return 0;
	}

	@Override
	public String getSeverity()
	{
		return severity;
	}

	@Override
	public String getConfidence()
	{
		return confidence;
	}

	@Override
	public String getIssueBackground()
	{
        return "OAUTH 2.0 is an open standard (RFC 6749) that allows applications to get access to protected "
        +"resources and APIs on behalf of users without accessing their credentials.\n"
        +"OAUTHv2 defines overarching schemas for granting authorization but does not describes how "
        +"to actually perform authentication.\nOpenID instead is an OAUTHv2 extension which striclty defines some "
        +"authentication patterns to grant access to users by authenticating them through another service "
        +"or provider.\n"
        +"There are many different ways to implement OAUTHv2 and OpenID login processes. They are widely "
        +"supported by identity providers and API vendors and could be used in various contexts"
        +"as in Web, Mobile, and Desktop applications.\nCause of their complexity and versatility, OAUTHv2 "
        +"and OpenID are both extremely common and inherently prone to implementation mistakes. This can result "
        +"in various kind of vulnerabilities, allowing attackers to obtain confidential user data and "
        +"potentially completely bypass authentication.";
	}

	@Override
	public String getRemediationBackground()
	{
		return "To prevent OAUTHv2 and OpenID security issues, it is essential for the involved entities "
        +"(OAUTHv2/OpenID Service-Provider and Client-Application) to implement robust validation of the key inputs. Given their "
        +"complexity, it is important for developers to implement carefully OAUTHv2 and OpenID to make them "
        +"as secure as possible.\nIt is important to note that vulnerabilities can arise both on "
        +"the side of the Client-Application and the Service-Provider itself. "
        +"Even if your own implementation is rock solid, you're still ultimately reliant on the "
        +"application at the other end being equally robust.\n<br><br>"
        +"For OAUTHv2/OpenID Service-Providers:\n"
        +"<ul><li>Require Client-Applications to register a whitelist of valid <code>redirect_uri</code> "
        +"values. Wherever possible, use strict byte-for-byte comparison to validate the URI in "
        +"any incoming requests. Only allow complete and exact matches rather than using pattern "
        +"matching. This prevents attackers from accessing other pages on the whitelisted "
        +"domains.</li><li>Enforce use of the <code>state</code> parameter. Its value should be bound "
        +"to the user's session by including some unguessable, session-specific data, such "
        +"as a hash containing the session cookie. This helps protect users against CSRF-like "
        +"attacks. It also makes it much more difficult for an attacker to use any stolen "
        +"authorization codes.</li><li>On the Resource-Server, make sure you verify that the "
        +"access token was issued to the same <code>client_id</code> that is making the request. "
        +"Check also the <code>scope</code> parameter in all requests to make sure that this matches "
        +"the scope for which the token was originally granted.</li>"
        +"<li>If using OAUTHv2 (or OpenID) Authorization Code Flow make sure to invalidate "
        +"each authorization code after its first use at the Resource-Server endpoint. In addition "
        +"attackers that retrieve unused authorizaton codes (stealed or brute-forced) could be able "
        +"to use them regardless of how long ago they were issued. To mitigate this potential issue, "
        +"unused authorization codes should expire after 10-15 minutes.</li></ul>\n<br> "
        +"For OAUTHv2/OpenID Client-Applications:\n"
        +"<ul><li>Developers have to fully understand the details of how OAUTHv2 (or OpenID) works "
        +"before implementing it. Many vulnerabilities are caused by a simple lack of "
        +"understanding of what exactly is happening at each stage and how this can "
        +"potentially be exploited.</li><li>Use the <code>state</code> parameter even though it is "
        +"not mandatory. Its value should be bound to the user's session by including some unguessable, "
        +"session-specific data, such as a hash containing the session cookie. This helps protect users "
        +"against CSRF-like attacks, and makes it much more difficult for an attacker to use any stolen "
        +"authorization codes.</li><li>When developing OAUTHv2/OpenID processes for Mobile (or Native desktop) "
        +"Client-Applications, it is often not possible to keep the <code>client_secret</code> private. "
        +"In these situations, the PKCE (RFC 7636) mechanism may be used to provide additional "
        +"protection against access code interception or leakage.</li><li>When using the "
        +"OpenID parameter <code>id_token</code>, make sure it is properly validated according to the JSON "
        +"Web Signature, JSON Web Encryption, and OpenID specifications.</li><li>Developers "
        +"should be careful with authorization codes (they may be leaked via Referer headers "
        +"when external images, scripts, or CSS content is loaded). It is also important to "
        +"not include them in dynamically generated JavaScript files as they may be "
        +"executed from external domains.</li><li>Developers should use a secure "
        +"storage mechanism for access token and refresh token on client-side (i.e. use "
        +"Keychain/Keystore for mobile apps, use browser in-memory for web apps, etc.). "
        +"It is discouraged to store tokens on browsers local storage, because they will be "
        +"accessible by Javascript (XSS)</li><li>If possible use short lived access tokens "
        +"(i.e. expiration 30 minutes)</li>"
        +"<li>The OAUTHv2 Implicit Flow is insecure and considered deprecated by specifications, "
        +"avoid to use it and instead adopt OAUTHv2 Authorization Code Flow. "
        +"At the same times, developers should be careful when implementing OpenID Implicit Flow "
        +"because when not properly configured it could be vulnerable to access token leakage and "
        +"access token replay. Also avoid to use any Implicit Flow (OAUTHv2 and OpenID) in Mobile "
        +"application contexts.</li></ul>\n<br><br>"
        +"<b>References:</b><br><ul>"
        +"<li><a href=\"https://datatracker.ietf.org/doc/html/rfc6749\">https://datatracker.ietf.org/doc/html/rfc6749</a></li>"
        +"<li><a href=\"https://datatracker.ietf.org/doc/html/rfc6819\">https://datatracker.ietf.org/doc/html/rfc6819</a></li>"
        +"<li><a href=\"https://datatracker.ietf.org/doc/html/rfc6750\">https://datatracker.ietf.org/doc/html/rfc6750</a></li>"
        +"<li><a href=\"https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics-09\">https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics-09</a></li>"
        +"<li><a href=\"https://oauth.net/2/\">https://oauth.net/2/</a></li>"
        +"<li><a href=\"https://openid.net/connect/\">https://openid.net/connect/</a></li>"
        +"<li><a href=\"https://openid.net/specs/openid-connect-core-1_0.html\">https://openid.net/specs/openid-connect-core-1_0.html</a></li>"
        +"<li><a href=\"https://portswigger.net/web-security/oauth\">https://portswigger.net/web-security/oauth</a></li>"
        +"<li><a href=\"https://portswigger.net/web-security/oauth/openid\">https://portswigger.net/web-security/oauth/openid</a></li></ul>\n";
	}

	@Override
	public String getIssueDetail()
	{
		return detail;
	}

	@Override
	public String getRemediationDetail()
	{
		return null;
	}

	@Override
	public IHttpRequestResponse[] getHttpMessages()
	{
		return httpMessages;
	}

	@Override
	public IHttpService getHttpService()
	{
		return httpService;
	}
}
