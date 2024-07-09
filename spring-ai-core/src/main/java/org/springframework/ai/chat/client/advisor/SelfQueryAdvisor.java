package org.springframework.ai.chat.client.advisor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.AdvisedRequest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

/**
 * Advisor that structures the user's query to match the request schema provided below.
 * Inspired from the langchain SelfQueryRetriever.
 */
public class SelfQueryAdvisor extends QuestionAnswerAdvisor {

	private static final String STRUCTURED_REQUEST_PROMPT = """
			Your goal is to structure the user's query to match the request schema provided below.\s
			
			  {schema}
			<< Structured Request Schema >>
			When responding use directly parsable JSON object formatted in the following schema:
			
			{{"query": "string", "filter": "string"}}
			
			The response JSON object should have the fields "query" and "filter"."query" is a text string to compare to document contents. "filter" is a logical condition statement for filtering documents. Any conditions in the "filter" should not be mentioned in the "query" as well.
			
			A logical condition statement is composed of one or more comparison and logical operation statements.
			
			A comparison statement takes the form: `attr comp val`:
			- `comp` ({allowed_comparators}): comparator
			- `attr` (string):  name of attribute to apply the comparison to
			- `val` (string): is the comparison value. Enclose val with single quotes.
			- if `val` is a list of values, it should be in the format `attr == 'val1' OR attr == 'val2' OR ...`
			A logical operation statement takes the form `statement1 op statement2 op ...`:
			- `op` ({allowed_operators}): logical operator
			- `statement1`, `statement2`, ... (comparison statements or logical operation statements): one or more statements to apply the operation to
			
			Make sure that you only use the comparators and logical operators listed above and no others.
			Make sure that filters only refer to attributes that exist in the data source.
			Make sure that filters only use the attributed names with its function names if there are functions applied on them.
			Make sure that filters only use format `YYYY-MM-DD` when handling date data typed values.
			Make sure that filters take into account the descriptions of attributes and only make comparisons that are feasible given the type of data being stored.
			Make sure that filters are only used as needed. If there are no filters that should be applied return "NO_FILTER" for the filter value.
			
			User query: {query}
			""";

	private final String metadataFieldInfoAsJson;
	private final SearchRequest searchRequest;
	private final ChatModel chatModel;

	private static final ObjectMapper objectMapper = new ObjectMapper();
	private static final List<String> allowedComparators = List.of("==", "!=", ">", ">=", "<", "<=", "-", "+");
	private static final List<String> allowedOperators = List.of("AND", "OR", "IN", "NIN", "NOT");

	public SelfQueryAdvisor(List<AttributeInfo> metadataFieldInfo, VectorStore vectorStore, SearchRequest searchRequest, ChatModel chatModel) {
		super(vectorStore, searchRequest);
		try {
			this.metadataFieldInfoAsJson = objectMapper.writeValueAsString(metadataFieldInfo);
		} catch (Exception e) {
			throw new RuntimeException("Failed to serialize metadata field info", e);
		}
		this.searchRequest = searchRequest;
		this.chatModel = chatModel;
	}

	@Override
	public AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> context) {
		String userQuery = request.userText();
		QueryFilter queryFilter = extractQueryFilter(userQuery);
		if (queryFilter.isFilterFound()) {
			searchRequest
					.withQuery(queryFilter.getQuery())
					.withFilterExpression(queryFilter.getFilter());
		}
		var fromAdvisedRequestWithSummaryQuery = AdvisedRequest.from(request).withUserText(queryFilter.query).build();
		return super.adviseRequest(fromAdvisedRequestWithSummaryQuery, context);
	}

	private QueryFilter extractQueryFilter(String userQuery) {
		String queryExtractionResult = chatModel.call(queryExtractionPrompt(userQuery)).getResult().getOutput().getContent();
		try {
			return objectMapper.readValue(queryExtractionResult, QueryFilter.class);
		} catch (Exception e) {
			throw new RuntimeException("Failed to parse query filter from response", e);
		}
	}

	private Prompt queryExtractionPrompt(String query) {
		PromptTemplate promptTemplate = new PromptTemplate(
				STRUCTURED_REQUEST_PROMPT,
				Map.of(
						"allowed_comparators", String.join(",", allowedComparators),
						"allowed_operators", String.join(",", allowedOperators),
						"schema", metadataFieldInfoAsJson,
						"query", query
				)
		);
		return new Prompt(promptTemplate.createMessage());
	}

	private static class QueryFilter {
		private String query;
		private String filter;

		public String getQuery() {
			return query;
		}

		public void setQuery(String query) {
			this.query = query;
		}

		public String getFilter() {
			return filter;
		}

		public void setFilter(String filter) {
			this.filter = filter;
		}

		public boolean isFilterFound() {
			return !"NO_FILTER".equals(filter);
		}
	}

	public static class AttributeInfo {
		private String name;
		private String type;
		private String description;

		public AttributeInfo() {
		}

		public AttributeInfo(String name, String type, String description) {
			this.name = name;
			this.type = type;
			this.description = description;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}
	}
}
