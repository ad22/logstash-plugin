/*
 * The MIT License
 *
 * Copyright 2014 Barnes and Noble College
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.plugins.logstash.persistence;

import static com.google.common.collect.Ranges.closedOpen;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;

import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import com.google.common.collect.Range;

import jenkins.plugins.logstash.utils.SSLHelper;


/**
 * Elastic Search Data Access Object.
 *
 * @author Liam Newman
 * @since 1.0.4
 */
public class ElasticSearchDao extends AbstractLogstashIndexerDao {

  private final HttpClientBuilder clientBuilder;
  private final URI uri;
  private final String auth;
  private final Range<Integer> successCodes = closedOpen(200,300);

  private String username;
  private String password;
  private String mimeType;
  private KeyStore customKeyStore;

  //primary constructor used by indexer factory
  public ElasticSearchDao(URI uri, String username, String password) {
    this(null, uri, username, password);
  }

  // Factored for unit testing
  ElasticSearchDao(HttpClientBuilder factory, URI uri, String username, String password) {

    if (uri == null)
    {
      throw new IllegalArgumentException("uri field must not be empty");
    }

    this.uri = uri;
    this.username = username;
    this.password = password;


    try
    {
      uri.toURL();
    }
    catch (MalformedURLException e)
    {
      throw new IllegalArgumentException(e);
    }

    if (StringUtils.isNotBlank(username)) {
      auth = Base64.encodeBase64String((username + ":" + StringUtils.defaultString(password)).getBytes(StandardCharsets.UTF_8));
    } else {
      auth = null;
    }

    clientBuilder = factory == null ? HttpClientBuilder.create() : factory;
  }


  public URI getUri()
  {
    return uri;
  }
  public String getHost()
  {
    return uri.getHost();
  }

  public String getScheme()
  {
    return uri.getScheme();
  }

  public int getPort()
  {
    return uri.getPort();
  }

  public String getUsername()
  {
    return username;
  }

  public String getPassword()
  {
      return password;
  }

  public String getKey()
  {
    return uri.getPath();
  }

  public String getMimeType() {
    return this.mimeType;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  public KeyStore getCustomKeyStore() {
    return this.customKeyStore;
  }

  String getAuth()
  {
    return auth;
  }

  public void setCustomKeyStore(KeyStore customKeyStore) throws
          CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
    SSLHelper.setClientBuilderSSLContext(this.clientBuilder, customKeyStore);
    this.customKeyStore = customKeyStore;
  }

  HttpPost getHttpPost(String data, String id) {
    URI idUri = uri.resolve(uri.getPath() + "/" + id);
    HttpPost postRequest = new HttpPost(idUri);
    String mimeType = this.getMimeType();
    // char encoding is set to UTF_8 since this request posts a JSON string
    StringEntity input = new StringEntity(data, StandardCharsets.UTF_8);
    mimeType = (mimeType != null) ? mimeType : ContentType.APPLICATION_JSON.toString();
    input.setContentType(mimeType);
    postRequest.setEntity(input);
    if (auth != null) {
      postRequest.addHeader("Authorization", "Basic " + auth);
    }
    return postRequest;
  }

  @Override
  public void push(String data, String id) throws IOException {
    HttpPost post = getHttpPost(data, id);

    try (CloseableHttpClient httpClient = clientBuilder.build(); CloseableHttpResponse response = httpClient.execute(post)) {
      if (!successCodes.contains(response.getStatusLine().getStatusCode())) {
        throw new IOException(this.getErrorMessage(response));
      }
    }
  }

  private String getErrorMessage(CloseableHttpResponse response) {
    ByteArrayOutputStream byteStream = null;
    PrintStream stream = null;
    try {
      byteStream = new ByteArrayOutputStream();
      stream = new PrintStream(byteStream, true, StandardCharsets.UTF_8.name());

      try {
        stream.print("HTTP error code: ");
        stream.println(response.getStatusLine().getStatusCode());
        stream.print("URI: ");
        stream.println(uri.toString());
        stream.println("RESPONSE: " + response.toString());
        response.getEntity().writeTo(stream);
      } catch (IOException e) {
        stream.println(ExceptionUtils.getStackTrace(e));
      }
      stream.flush();
      return byteStream.toString(StandardCharsets.UTF_8.name());
    }
    catch (UnsupportedEncodingException e)
    {
      return ExceptionUtils.getStackTrace(e);
    } finally {
      if (stream != null) {
        stream.close();
      }
    }
  }


  @Override
  public String getDescription()
  {
    return uri.toString();
  }
}
