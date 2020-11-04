# How to run

1. `git clone https://github.com/paulcwarren/abac_spike_1`
2. `cd abac-spike-1` 
3. `docker run -d -p 8983:8983 paulcwarren/solr`
4. `cd abac-spike-1/src/test/resources/policies`
5. `docker run -v $PWD:/policies -p 8181:8181 openpolicyagent/opa:0.20.5 run --server --log-level debug --bundle /policies'`
6. `mvn clean test`

# How to test OPA

```
# run OPA
cd abac-spike-1/src/test/resources/policies
docker run -v $PWD:/policies -p 8181:8181 openpolicyagent/opa:0.20.5 run --server --log-level debug --bundle /policies

# execute a query
cd abac-spike-1/src/test/resources/policies
curl -X POST --data-binary @query.json 127.0.0.1:8181/v1/compile | python -m json.tool
```
