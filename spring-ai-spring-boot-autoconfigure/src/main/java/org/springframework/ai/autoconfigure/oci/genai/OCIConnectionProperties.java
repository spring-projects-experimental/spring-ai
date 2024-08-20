/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.oci.genai;

import java.nio.file.Paths;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * @author Anders Swanson
 */
@ConfigurationProperties(OCIConnectionProperties.CONFIG_PREFIX)
public class OCIConnectionProperties {

	private static final String DEFAULT_PROFILE = "DEFAULT";

	public static final String CONFIG_PREFIX = "spring.ai.oci.genai";

	public enum AuthenticationType {

		FILE("file"), INSTANCE_PRINCIPAL("instance-principal"), WORKLOAD_IDENTITY("workload-identity"),
		SIMPLE("simple");

		private final String authType;

		AuthenticationType(String authType) {
			this.authType = authType;
		}

		public String getAuthType() {
			return this.authType;
		}

	}

	private AuthenticationType authenticationType = AuthenticationType.FILE;

	private String profile;

	private String file = Paths.get(System.getProperty("user.home"), ".oci", "config").toString();

	private String tenantId;

	private String userId;

	private String fingerprint;

	private String privateKey;

	private String passPhrase;

	private String region = "us-chicago-1";

	private String endpoint;

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getPassPhrase() {
		return passPhrase;
	}

	public void setPassPhrase(String passPhrase) {
		this.passPhrase = passPhrase;
	}

	public String getPrivateKey() {
		return privateKey;
	}

	public void setPrivateKey(String privateKey) {
		this.privateKey = privateKey;
	}

	public String getFingerprint() {
		return fingerprint;
	}

	public void setFingerprint(String fingerprint) {
		this.fingerprint = fingerprint;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getTenantId() {
		return tenantId;
	}

	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

	public String getFile() {
		return file;
	}

	public void setFile(String file) {
		this.file = file;
	}

	public String getProfile() {
		return StringUtils.hasText(profile) ? profile : DEFAULT_PROFILE;
	}

	public void setProfile(String profile) {
		this.profile = profile;
	}

	public AuthenticationType getAuthenticationType() {
		return authenticationType;
	}

	public void setAuthenticationType(AuthenticationType authenticationType) {
		this.authenticationType = authenticationType;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

}
