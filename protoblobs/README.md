
# protoblobs

This directory contains a set of example OTLP payloads, which are OTLP traces
coming from the splunk-otel-android sdk.

```bash
git clone git@github.com:breedx-splk/splunk-otel-android.git
cd splunk-otel-android/protoblobs
```

These raw/binary data blobs can be sent with curl to an endpoint:

```bash
curl -i -X POST -H 'Content-Type: application/x-protobuf' \
  --data-binary @traces_83302952038813123141711664949962 \
  http://localhost:4318/v1/traces
```

OTLP http receiver typically listens on port 4318. Change the port to 
suit your needs.
