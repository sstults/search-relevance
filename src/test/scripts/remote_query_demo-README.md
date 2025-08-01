# Remote Query Capability Demo Scripts

This directory contains demonstration scripts for the remote query capability of the OpenSearch Search Relevance plugin. The scripts showcase how to compare search performance between OpenSearch and external search engines using identical datasets and standardized evaluation metrics.

## Overview

The remote query capability enables OpenSearch to:
- Connect to external search engines via HTTP/HTTPS
- Transform queries between different search engine formats  
- Normalize responses for consistent evaluation
- Run comparative experiments across multiple search platforms
- Generate standardized metrics (NDCG, MAP, MRR) for objective comparison

## Scripts

### 1. `remote_query_demo.sh` (Recommended)

**The consolidated, working demonstration script** that provides a complete remote query capability demonstration.

**Features:**
- Comprehensive error handling and dependency checking
- Robust Docker and service management
- Reliable data processing with validation
- Template transformation demonstrations
- Search comparison across OpenSearch and Solr
- Sample metrics comparison
- Complete cleanup on exit

**Usage:**
```bash
cd src/test/scripts
./remote_query_demo.sh
```

**What it demonstrates:**
1. Infrastructure setup (Solr container running in background, schema configuration)
2. Data loading (identical ESCI dataset in both systems)
3. Query template transformation (OpenSearch ↔ Solr)
4. Response template normalization
5. Search comparison with sample queries
6. Remote search configuration concepts
7. Sample metrics comparison
8. Automatic cleanup when demo completes

### 2. Other Available Scripts

The following scripts are also available in this directory for various search relevance tasks:
- `demo.sh` - General demonstration script
- `demo_hybrid_optimizer.sh` - Hybrid search optimization demo
- `create_*.sh` - Various utility scripts for creating experiments, query sets, and configurations
- `get_experiment.sh` - Retrieve experiment results
- `list_*.sh` - List existing configurations and query sets

## Prerequisites

### Required Tools
- **Docker** - For running Solr container
- **curl** - For API interactions
- **bash** - Shell environment

### Optional Tools
- **jq** - For JSON formatting (recommended)
- **wget** - Alternative to curl for downloads

### Required Services
- **OpenSearch** - Running on localhost:9200 with Search Relevance plugin installed
- **Docker** - For Solr container management

## Quick Start

1. **Start OpenSearch** with the Search Relevance plugin:
   ```bash
   # Using docker-compose (recommended)
   docker compose up -d
   
   # OR using docker directly (background mode)
   docker run -d -p 9200:9200 -e 'discovery.type=single-node' opensearchproject/opensearch:latest
   
   # OR using gradle (requires Java 21)
   ./gradlew run --preserve-data
   ```

2. **Run the consolidated demo**:
   ```bash
   cd src/test/scripts
   ./remote_query_demo.sh
   ```

3. **Follow the interactive output** - The script will guide you through each step

## Implementation Status

The remote search feature is currently **75% complete**:

### ✅ Completed Components
- Data models (RemoteSearchConfiguration, Cache, Failure)
- HTTP client with rate limiting and authentication
- Response mapping and template processing
- Caching layer with TTL management
- Comprehensive test coverage

### 🔄 In Development
- REST API endpoints for configuration management
- ExperimentTaskManager integration for remote search execution
- Transport layer implementation

## Key Concepts Demonstrated

### Query Template Transformation
```bash
# OpenSearch multi_match query
{"query":{"multi_match":{"query":"tv","fields":["title","category"]}}}

# Transformed to Solr edismax query  
q=title:(tv)+OR+category:(tv)&wt=json&rows=10
```

### Response Normalization
```bash
# Solr response format
{"response":{"numFound":42,"docs":[...]}}

# Normalized to OpenSearch format
{"hits":{"total":{"value":42},"hits":[...]}}
```

### Remote Search Configuration
```json
{
  "name": "Solr Remote Search",
  "connectionUrl": "http://localhost:8983/solr/ecommerce/select",
  "queryTemplate": "q=title:(${queryText})+OR+category:(${queryText})",
  "responseTemplate": "{\"hits\": {\"hits\": \"${response.docs}\"}}", 
  "maxRequestsPerSecond": 10,
  "cacheDurationMinutes": 60
}
```

## Use Cases

### 1. Search Engine Comparison
Compare OpenSearch vs Solr relevance performance using identical datasets and standardized metrics.

### 2. Migration Validation  
Validate search quality when migrating to OpenSearch by running experiments against both legacy and new systems.

### 3. A/B Testing Across Systems
Test new search algorithms against production systems safely.

### 4. Multi-Vendor Evaluation
Evaluate multiple search technologies using standardized comparison criteria.

## Troubleshooting

### Common Issues

1. **Port Conflicts**: Ensure ports 8983 (Solr) and 9200 (OpenSearch) are available
2. **Docker Issues**: Verify Docker is running and accessible
3. **Memory Issues**: Solr and OpenSearch both require adequate memory
4. **Plugin Missing**: Ensure Search Relevance plugin is installed in OpenSearch

### Debug Mode

For detailed debugging:
- Check Docker logs: `docker logs solr_demo`
- Verify OpenSearch: `curl http://localhost:9200/_cat/plugins`
- Check plugin status: `curl http://localhost:9200/_cluster/settings`

### Managing Background Containers

When running containers in the background:

**Check running containers:**
```bash
docker ps
```

**Stop background containers:**
```bash
# Stop OpenSearch
docker stop <opensearch_container_id>

# Stop Solr (if running separately)
docker stop solr_demo
```

**View container logs:**
```bash
# OpenSearch logs
docker logs <opensearch_container_id>

# Solr logs
docker logs solr_demo
```

**Clean up containers:**
```bash
# Remove stopped containers
docker rm <container_id>

# Remove all stopped containers
docker container prune
```

## Expected Output

The scripts provide colored, structured output showing:

1. **Setup Progress**: Service startup, schema configuration, data loading
2. **Template Testing**: Query/response transformation validation
3. **Search Comparison**: Side-by-side results from both systems
4. **Configuration Concepts**: What the full remote search capability will look like
5. **Sample Metrics**: Comparative analysis examples

## Future Enhancements

### Additional Search Engines
The remote query capability can be extended to support:
- Elasticsearch clusters
- Amazon CloudSearch  
- Azure Cognitive Search
- Custom search APIs

### Advanced Features
- OAuth and certificate-based authentication
- Response streaming for large result sets
- Advanced template processing
- Integration with external cache systems

## Related Documentation

- [Remote Query Feature Design](../../docs/feature-design/remote-query.md)
- [Search Relevance Plugin Documentation](https://opensearch.org/docs/latest/search-plugins/search-relevance/)
- [ESCI Dataset Information](../data-esci/README.md)

## Support

For issues or questions:
1. Check the OpenSearch Search Relevance plugin documentation
2. Review the feature design document
3. Examine script output for specific error messages
4. Verify all prerequisites are met

## Contributing

When modifying these scripts:
1. Maintain comprehensive error handling
2. Include progress indicators and clear logging
3. Ensure proper cleanup on both success and failure
4. Test with and without optional dependencies (like jq)
5. Update this documentation accordingly
