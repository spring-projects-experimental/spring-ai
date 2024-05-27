package org.springframework.ai.autoconfigure;

/**
 * @author Josh Long
 */
public class CommonVectorStoreProperties {

	private boolean initializeSchema = true;

	public boolean isInitializeSchema() {
		return initializeSchema;
	}

	public void setInitializeSchema(boolean initializeSchema) {
		this.initializeSchema = initializeSchema;
	}

}
