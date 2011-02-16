package edu.isi.misd.tagfiler.client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.AbstractHttpMessage;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.util.EntityUtils;
import org.htmlparser.Node;
import org.htmlparser.Parser;
import org.htmlparser.filters.TagNameFilter;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.htmlparser.util.Translate;

import edu.isi.misd.tagfiler.AbstractTagFilerApplet;
import edu.isi.misd.tagfiler.util.ClientUtils;

/**
 * Class for HTTP requests
 * 
 * @author Serban Voinea
 *
 */
public class JakartaClient  implements ClientURL {
	/**
	 * @param args
	 */
	
    // client used to connect with the tagfiler server
	private DefaultHttpClient httpclient;
	
    // client used to connect with the tagfiler server
	protected boolean browser = true;
	
    /**
     * Constructor
     * 
     * @param connections
     *            the maximum number of HTTP connections
     */
	public JakartaClient(int maxConnections, int socketBufferSize) {
		try {
			init(maxConnections, socketBufferSize);
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
    /**
     * Check we have a valid client
     * 
     * @return true if the client is valid
     */
	public boolean isValid() {
		return httpclient != null;
	}
	
    /**
     * Initialize the HTTP client
     * 
     * @param connections
     *            the maximum number of HTTP connections
     */
	private void init(int maxConnections, int socketBufferSize) throws Throwable {
		TrustManager easyTrustManager = new X509TrustManager() {

		    public void checkClientTrusted(
		            X509Certificate[] chain,
		            String authType) throws CertificateException {
		        // Oh, I am easy!
		    }

		    public void checkServerTrusted(
		            X509Certificate[] chain,
		            String authType) throws CertificateException {
		        // Oh, I am easy!
		    }

		    public X509Certificate[] getAcceptedIssuers() {
		        return null;
		    }
		    
		};
		
		SSLContext sslcontext = SSLContext.getInstance("SSL");
		sslcontext.init(null, new TrustManager[] { easyTrustManager }, null);
		SSLSocketFactory sf = new SSLSocketFactory(sslcontext); 
		sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		
		BasicHttpParams params = new BasicHttpParams();
		params.setParameter("http.protocol.handle-redirects", false);
		params.setParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, socketBufferSize);
		
		// enable parallelism
		ConnPerRouteBean connPerRoute = new ConnPerRouteBean(maxConnections);
		ConnManagerParams.setMaxTotalConnections(params, maxConnections >= 2 ? maxConnections : 2);
		ConnManagerParams.setMaxConnectionsPerRoute(params, connPerRoute);
		
        SchemeRegistry schemeRegistry = new SchemeRegistry(); 
        Scheme sch = new Scheme("https", sf, 443);
        schemeRegistry.register(sch);
        //schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        ClientConnectionManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);

        httpclient = new DefaultHttpClient(cm, params);
    	BasicCookieStore cookieStore = new BasicCookieStore();
    	httpclient.setCookieStore(cookieStore);
	}

    /**
     * Execute a login request.
     * If success, it will get a cookie
     * 
     * @param url
     *            the query url
     * @param user
     *            the userid
     * @param password
     *            the password
     * @return the HTTP Response
     */
    public ClientURLResponse login(String url, String user, String password) {
		browser = false;
        HttpPost httppost = new HttpPost(url);
		List<NameValuePair> formparams = new ArrayList<NameValuePair>();
		formparams.add(new BasicNameValuePair("username", user));
		formparams.add(new BasicNameValuePair("password", password));
		UrlEncodedFormEntity entity = null;
		try {
			entity = new UrlEncodedFormEntity(formparams, "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		httppost.setEntity(entity);
		return execute(httppost, null);
    }
    
    /**
     * Verify that we have a valid control number
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse verifyValidControlNumber(String url, String cookie) {
    	HttpHead httphead = new HttpHead(url);
        httphead.setHeader("Accept", "text/uri-list");
        httphead.setHeader("Content-Type", "application/octet-stream");
		return execute(httphead, cookie);
    }
    
    /**
     * Get the length of a file to be downloaded
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse getFileLength(String url, String cookie) {
    	HttpHead httphead = new HttpHead(url);
        httphead.setHeader("Accept", "text/uri-list");
        httphead.setHeader("Content-Type", "application/octet-stream");
		return execute(httphead, cookie);
    }
    
    /**
     * Get the list of the file names to be downloaded.
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse getDataSet(String url, String cookie) {
		HttpGet httpget = new HttpGet(url);
    	httpget.setHeader("Accept", "text/uri-list");
    	httpget.setHeader("Content-Type", "application/octet-stream");
		return execute(httpget, cookie);
	}
    
    /**
     * Get the value of a tag
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse getTagValue(String url, String cookie) {
		HttpGet httpget = new HttpGet(url);
    	httpget.setHeader("Content-Type", "application/octet-stream");
		return execute(httpget, cookie);
	}
    
    /**
     * Get the values of a dataset tags
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse getTagsValues(String url, String cookie) {
		HttpGet httpget = new HttpGet(url);
    	httpget.setHeader("Accept", "application/json");
		return execute(httpget, cookie);
	}
    
    /**
     * Get the content of a file to be downloaded
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse downloadFile(String url, String cookie) {
		HttpGet httpget = new HttpGet(url);
    	httpget.setHeader("Content-Type", "application/octet-stream");
		return execute(httpget, cookie);
	}
    
    /**
     * Get the content of a file to be downloaded
     * 
     * @param url
     *            the query url
     * @param length
     *            the number of bytes to read
     * @param first
     *            the first byte to read
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse downloadFile(String url,  long length, long first, String cookie) {
		HttpGet httpget = new HttpGet(url);
    	httpget.setHeader("Content-Type", "application/octet-stream");
    	httpget.setHeader("Range", "bytes="+first+"-"+(first+length-1));
		return execute(httpget, cookie);
	}
    
    /**
     * Get a new sequence number
     * 
     * @param url
     *            the query url
     * @param table
     *            the sequence table
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse getSequenceNumber(String url, String table, String cookie) {
		HttpPost httppost = new HttpPost(url);
		httppost.setHeader("Content-Type", "application/x-www-form-urlencoded");
		List<NameValuePair> formparams = new ArrayList<NameValuePair>();
		formparams.add(new BasicNameValuePair("table", table));
		UrlEncodedFormEntity entity = null;
		try {
			entity = new UrlEncodedFormEntity(formparams, "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		httppost.setEntity(entity);
		return execute(httppost, cookie);
	}
    
    /**
     * Checks for and saves updated session cookie
     * 
     * @param response
     *            the Jersey response
     * @param applet
     *            the applet
     * @param cookie
     *            the current cookie
     * @return the curernt cookie or a new replacement
     */
    public String updateSessionCookie(AbstractTagFilerApplet applet, String cookie) {
    	for (Cookie candidate : httpclient.getCookieStore().getCookies()) {
        	if (candidate.getName().equals("webauthn")) {
        		String value = getCookieValue(candidate);
        		if (browser) {
                    ClientUtils.setCookieInBrowser(applet, value);
        		}
        		
        		return value;
        	}
        }
        
        return cookie;
	}
    
    /**
     * Uploads a set of given files with a specified dataset name.
     * 
     * @param url
     *            the query url
     * @param datasetURLBody
     *            the body of the dataset
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse postFileData(String url, String datasetURLBody, String cookie) {
		HttpPost httppost = new HttpPost(url);
		httppost.setHeader("Content-Type", "application/x-www-form-urlencoded");
		try {
			httppost.setEntity(new StringEntity(datasetURLBody));
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return execute(httppost, cookie);
	}
    
    /**
     * Uploads a set of given files with a specified dataset name.
     * 
     * @param url
     *            the query url
     * @param datasetURLBody
     *            the body of the dataset
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse putFileData(String url, String datasetURLBody, String cookie) {
		HttpPut httpput = new HttpPut(url);
    	httpput.setHeader("Content-Type", "application/x-www-form-urlencoded");
		try {
			httpput.setEntity(new StringEntity(datasetURLBody));
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return execute(httpput, cookie);
	}
    
    /**
     * Uploads a set of given files with a specified dataset name.
     * 
     * @param url
     *            the query url
     * @param file
     *            the file to be uploaded
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse postFile(String url, File file, String cookie) {
		HttpPut httpput = new HttpPut(url);
    	httpput.setHeader("Content-Type", "application/octet-stream");
    	FileEntity fileEntity = new FileEntity(file, "binary/octet-stream");
    	fileEntity.setChunked(false);
    	httpput.setEntity(fileEntity);
		return execute(httpput, cookie);
	}
    
    /**
     * Uploads a file block.
     * 
     * @param url
     *            the query url
     * @param inputStream
     *            the InputStream where to read from
     * @param length
     *            the number of bytes to read
     * @param first
     *            the first byte to read
     * @param fileLength
     *            the file length
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse postFile(String url, InputStream inputStream, long length, long first, long fileLength, String cookie) {
		HttpPut httpput = new HttpPut(url);
    	httpput.setHeader("Content-Type", "application/octet-stream");
    	if (first != 0) {
        	httpput.setHeader("Content-Range",  "bytes "+first+"-"+(first+length-1)+"/"+fileLength);
    	}
    	InputStreamEntity inputStreamEntity = new InputStreamEntity(inputStream, length);
    	inputStreamEntity.setChunked(false);
    	httpput.setEntity(inputStreamEntity);
		return execute(httpput, cookie);
	}
    
    /**
     * Uploads a file block.
     * 
     * @param url
     *            the query url
     * @param inputStream
     *            the InputStream where to read from
     * @param length
     *            the number of bytes to read
     * @param first
     *            the first byte to read
     * @param fileLength
     *            the file length
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse postFile(String url, byte[] entity, long length, long first, long fileLength, String cookie) {
		HttpPut httpput = new HttpPut(url);
    	httpput.setHeader("Content-Type", "application/octet-stream");
    	if (first != 0) {
        	httpput.setHeader("Content-Range",  "bytes "+first+"-"+(first+length-1)+"/"+fileLength);
    	}
    	ByteArrayEntity inputStreamEntity = new ByteArrayEntity(entity);
    	inputStreamEntity.setChunked(false);
    	httpput.setEntity(inputStreamEntity);
		return execute(httpput, cookie);
	}
    
    /**
     * Validate an upload/download.
     * The server will log the action result
     * 
     * @param url
     *            the query url
     * @param status
     *            the action status (success or failure)
     * @param key
     *            the set id
     * @param study_size
     *            the size of the study
     * @param count
     *            the number of files of the study
     * @param direction
     *            the action direction (upload or download)
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse validateAction(String url, String key, String status, long study_size, int count,  String direction, String cookie) {
		HttpPut httpput = new HttpPut(url);
		List<NameValuePair> formparams = new ArrayList<NameValuePair>();
		formparams.add(new BasicNameValuePair("study_size", ""+study_size));
		formparams.add(new BasicNameValuePair("status", status));
		formparams.add(new BasicNameValuePair("direction", direction));
		formparams.add(new BasicNameValuePair("count", ""+count));
		formparams.add(new BasicNameValuePair("key", key));
		UrlEncodedFormEntity entity = null;
		try {
			entity = new UrlEncodedFormEntity(formparams, "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		System.out.println("formparams: "+formparams);
		httpput.setEntity(entity);
		return execute(httpput, cookie);
	}
    
    /**
     * Delete a resource
     * 
     * @param url
     *            the url of the resource to be deleted
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse delete(String url, String cookie) {
		HttpDelete httpdelete = new HttpDelete(url);
		return execute(httpdelete, cookie);
	}
    
    private ClientURLResponse execute(HttpUriRequest request, String cookie) {
    	setCookie(cookie, request);
    	ClientURLResponse response = null;
		try {
			response = new JakartaClientResponse(httpclient.execute(request));
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return response;
    }
    
    /**
     * Set the cookie for the request
     * 
     * @param cookie
     *            the cookie to be set in the request
     * @param request
     *            the request to be sent
     */
    private void setCookie(String cookie, HttpUriRequest request) {
    	if (cookie != null) {
    		httpclient.getCookieStore().clear();
        	request.setHeader("Cookie", "webauthn="+cookie);
    	}
    }
    
    /**
     * Get the cookie for the request
     * @param cookie
     *            the cookie to be set in the request
     */
    private String getCookieValue(Cookie cookie) {
		String value;
    	try {
    		value = URLDecoder.decode(cookie.getValue(), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		
		return value;
    }
    
    synchronized private void debug(AbstractHttpMessage request) {
		Header headers[] = request.getAllHeaders();
        for (int i=0; i<headers.length; i++) {
        	try {
				System.out.println(headers[i].getName()+": "+URLDecoder.decode(headers[i].getValue(), "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
    }
    
    private class JakartaClientResponse implements ClientURLResponse {
    	private HttpResponse response;
    	
    	JakartaClientResponse(HttpResponse response) {
    		this.response = response;
    	}
    	
        /**
         * checks a particular header in the response to see if it matches an
         * expected regular expression pattern
         * 
         * @param response
         *            the client response
         * @param headerName
         *            name of the header to check
         * @param expectedPattern
         *            regular expression pattern to check
         * @return true if the header exists and matches the regular expression
         */
        public boolean checkResponseHeaderPattern(String headerName, String expectedPattern) {
            if (response == null ||
            		headerName == null || headerName.length() == 0 ||
            		expectedPattern == null) 
            	throw new IllegalArgumentException(""+response+", "+headerName+", "+expectedPattern);

            boolean matches = false;

            Header header = response.getFirstHeader(headerName);
            if (header != null) {
                String headerValue = response.getFirstHeader(headerName).getValue();
                if (headerValue.matches(expectedPattern)) {
                    matches = true;
                } else {
                    System.out.println("headerValue: " + headerValue);
                    System.out.println("expectedPattern: " + expectedPattern);
                }
            } else {
                System.out.println("The HttpResponse does not contain the header \"" + headerName + "\".");
            }
            return matches;
    	}
        
        /**
         * Return the HTTP status code
         * 
         */
        public int getStatus() {
    		return response.getStatusLine().getStatusCode();
    	}

        /**
         * Return the body as a string
         * 
         */
        public String getEntityString() {
        	try {
    			String result = EntityUtils.toString(response.getEntity());
    			return result;
    		} catch (ParseException e1) {
    			// TODO Auto-generated catch block
    			e1.printStackTrace();
    		} catch (IOException e1) {
    			// TODO Auto-generated catch block
    			e1.printStackTrace();
    		}
    		return null;
    	}

        /**
         * Return the location as a string
         * 
         */
        public String getLocationString() {
    		return response.getFirstHeader("Location").getValue();
    	}
        
        /**
         * Return the InputStream from where the body can be read
         * 
         */
        public InputStream getEntityInputStream() {
        	InputStream inputStream = null;
        	try {
        		inputStream = response.getEntity().getContent();
    		} catch (IllegalStateException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		
    		return inputStream;
    	}

        /**
         * Return the error message of an HTTP Request
         * 
         */
        public String getErrorMessage() {
        	String errormessage = "";
        	Parser parser = new Parser();
        	try {
				parser.setInputHTML(this.getEntityString());
				NodeList nl = parser.parse (null);
				NodeList pl = nl.extractAllNodesThatMatch(new TagNameFilter ("p"), true);
				if (pl.size() > 0) {
					Node p = pl.elementAt(0).getFirstChild();
					errormessage = Translate.decode(p.getText());
				}
			} catch (ParserException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	
    		return errormessage;
    	}

        /**
         * Get the response size
         * 
         */
        public long getResponseSize() {
        	long length = Long.parseLong(response.getFirstHeader("Content-Length").getValue());
        	return length;
    	}
        
        /**
         * Release the responses
         * 
         */
        public void release() {
            if (response.getEntity() != null) {
            	try {
    				response.getEntity().consumeContent();
    			} catch (IOException e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			}
            }
    	}

        /**
         * Utility to print the status as well as the headers
         */
        private void debug() {
        	if (response != null) {
            	System.out.println("Status: " + response.getStatusLine().getStatusCode());
    			Header headers[] = response.getAllHeaders();
    	        for (int i=0; i<headers.length; i++) {
    	        	try {
    					System.out.println(headers[i].getName()+": "+URLDecoder.decode(headers[i].getValue(), "UTF-8"));
    				} catch (UnsupportedEncodingException e) {
    					// TODO Auto-generated catch block
    					e.printStackTrace();
    				}
    	        }
        	}
            for (Cookie cookie : httpclient.getCookieStore().getCookies()) {
    			System.out.println("Cookie: " + cookie.getName() +"=" + cookie.getValue());
            }
        }
        
    }
    
}
