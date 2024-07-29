/*
 * Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.chat.memory;

import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.delete.Delete;
import com.datastax.oss.driver.api.querybuilder.delete.DeleteSelection;
import com.datastax.oss.driver.api.querybuilder.insert.InsertInto;
import com.datastax.oss.driver.api.querybuilder.insert.RegularInsert;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.datastax.oss.driver.shaded.guava.common.base.Preconditions;

import org.springframework.ai.chat.memory.CassandraChatMemoryConfig.SchemaColumn;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Create a CassandraChatMemory like <code>
CassandraChatMemory.create(CassandraChatMemoryConfig.builder().withTimeToLive(Duration.ofDays(1)).build());
</code>
 *
 * For example @see org.springframework.ai.chat.memory.CassandraChatMemory
 * 
 * @author Mick Semb Wever
 * @since 1.0.0
 */
public final class CassandraChatMemory implements ChatMemory {

	public static final String CONVERSATION_TS = CassandraChatMemory.class.getSimpleName() + "_message_timestamp";

	final CassandraChatMemoryConfig conf;

	private final PreparedStatement addUserStmt, addAssistantStmt, getStmt, deleteStmt;

	public static CassandraChatMemory create(CassandraChatMemoryConfig conf) {
		return new CassandraChatMemory(conf);
	}

	public CassandraChatMemory(CassandraChatMemoryConfig config) {
		this.conf = config;
		this.conf.ensureSchemaExists();
		this.addUserStmt = prepareAddStmt(this.conf.userColumn);
		this.addAssistantStmt = prepareAddStmt(this.conf.assistantColumn);
		this.getStmt = prepareGetStatement();
		this.deleteStmt = prepareDeleteStmt();
	}

	@Override
	public void add(String conversationId, List<Message> messages) {
		final AtomicLong instantSeq = new AtomicLong(Instant.now().toEpochMilli());
		messages.forEach((msg) -> {
			if (msg.getMetadata().containsKey(CONVERSATION_TS)) {
				msg.getMetadata().put(CONVERSATION_TS, Instant.ofEpochMilli(instantSeq.getAndIncrement()));
			}
			add(conversationId, msg);
		});
	}

	@Override
	public void add(String sessionId, Message msg) {

		Preconditions.checkArgument(
				!msg.getMetadata().containsKey(CONVERSATION_TS)
						|| msg.getMetadata().get(CONVERSATION_TS) instanceof Instant,
				"messages only accept metadata '%s' entries of type Instant", CONVERSATION_TS);

		msg.getMetadata().putIfAbsent(CONVERSATION_TS, Instant.now());

		PreparedStatement stmt;
		switch (msg.getMessageType()) {
			case USER -> stmt = addUserStmt;
			case ASSISTANT -> stmt = addAssistantStmt;
			default -> throw new IllegalArgumentException("Cant add type " + msg);
		}

		List<Object> primaryKeys = this.conf.primaryKeyTranslator.apply(sessionId);
		BoundStatementBuilder builder = stmt.boundStatementBuilder();

		for (int k = 0; k < primaryKeys.size(); ++k) {
			SchemaColumn keyColumn = this.conf.getPrimaryKeyColumn(k);
			builder = builder.set(keyColumn.name(), primaryKeys.get(k), keyColumn.javaType());
		}

		Instant instant = (Instant) msg.getMetadata().get(CONVERSATION_TS);

		builder = builder.setInstant(CassandraChatMemoryConfig.DEFAULT_EXCHANGE_ID_NAME, instant)
			.setString("message", msg.getContent());

		this.conf.session.execute(builder.build());
	}

	@Override
	public void clear(String sessionId) {

		List<Object> primaryKeys = this.conf.primaryKeyTranslator.apply(sessionId);
		BoundStatementBuilder builder = deleteStmt.boundStatementBuilder();

		for (int k = 0; k < primaryKeys.size(); ++k) {
			SchemaColumn keyColumn = this.conf.getPrimaryKeyColumn(k);
			builder = builder.set(keyColumn.name(), primaryKeys.get(k), keyColumn.javaType());
		}

		this.conf.session.execute(builder.build());
	}

	@Override
	public List<Message> get(String sessionId, int lastN) {

		List<Object> primaryKeys = this.conf.primaryKeyTranslator.apply(sessionId);
		BoundStatementBuilder builder = getStmt.boundStatementBuilder().setInt("lastN", lastN);

		for (int k = 0; k < primaryKeys.size(); ++k) {
			SchemaColumn keyColumn = this.conf.getPrimaryKeyColumn(k);
			builder = builder.set(keyColumn.name(), primaryKeys.get(k), keyColumn.javaType());
		}

		List<Message> messages = new ArrayList<>();
		for (Row r : this.conf.session.execute(builder.build())) {
			String assistant = r.getString(this.conf.assistantColumn);
			String user = r.getString(this.conf.userColumn);
			if (null != assistant) {
				messages.add(new AssistantMessage(assistant));
			}
			if (null != user) {
				messages.add(new UserMessage(user));
			}
		}
		return messages;
	}

	private PreparedStatement prepareAddStmt(String column) {
		RegularInsert stmt = null;
		InsertInto stmtStart = QueryBuilder.insertInto(this.conf.schema.keyspace(), this.conf.schema.table());
		for (var c : this.conf.schema.partitionKeys()) {
			stmt = (null != stmt ? stmt : stmtStart).value(c.name(), QueryBuilder.bindMarker(c.name()));
		}
		for (var c : this.conf.schema.clusteringKeys()) {
			stmt = stmt.value(c.name(), QueryBuilder.bindMarker(c.name()));
		}
		stmt = stmt.value(column, QueryBuilder.bindMarker("message"));
		return this.conf.session.prepare(stmt.build());
	}

	private PreparedStatement prepareGetStatement() {
		Select stmt = QueryBuilder.selectFrom(this.conf.schema.keyspace(), this.conf.schema.table()).all();
		for (var c : this.conf.schema.partitionKeys()) {
			stmt = stmt.whereColumn(c.name()).isEqualTo(QueryBuilder.bindMarker(c.name()));
		}
		for (int i = 0; i + 1 < this.conf.schema.clusteringKeys().size(); ++i) {
			String columnName = this.conf.schema.clusteringKeys().get(i).name();
			stmt = stmt.whereColumn(columnName).isEqualTo(QueryBuilder.bindMarker(columnName));
		}
		stmt = stmt.limit(QueryBuilder.bindMarker("lastN"));
		return this.conf.session.prepare(stmt.build());
	}

	private PreparedStatement prepareDeleteStmt() {
		Delete stmt = null;
		DeleteSelection stmtStart = QueryBuilder.deleteFrom(this.conf.schema.keyspace(), this.conf.schema.table());
		for (var c : this.conf.schema.partitionKeys()) {
			stmt = (null != stmt ? stmt : stmtStart).whereColumn(c.name()).isEqualTo(QueryBuilder.bindMarker(c.name()));
		}
		for (int i = 0; i + 1 < this.conf.schema.clusteringKeys().size(); ++i) {
			String columnName = this.conf.schema.clusteringKeys().get(i).name();
			stmt = stmt.whereColumn(columnName).isEqualTo(QueryBuilder.bindMarker(columnName));
		}
		return this.conf.session.prepare(stmt.build());
	}

}
