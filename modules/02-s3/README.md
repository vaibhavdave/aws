# Module 02 — S3 Object Storage (File Vault)

## Theory

**S3's data model is deliberately simple:** a bucket is a flat namespace (no real
directories — `folder/file.txt` is just a key containing a `/`), and every object
is an immutable blob identified by that key plus, if versioning is on, a version ID.
There's no update-in-place: "overwriting" an object is really "write a new version".

**Versioning** turns every `PutObject` on an existing key into a new version instead
of destroying the old one. `GetObject` without a version ID returns the latest; you
can still fetch any prior version explicitly. This is what makes accidental deletes
and overwrites recoverable — and it's why `FileVaultService.upload()` in this module
returns a `versionId` in its response.

**Multipart upload** is how S3 handles large objects: instead of one giant PUT, you
`CreateMultipartUpload` (get an upload ID), `UploadPart` for each chunk (each part
gets an ETag), then `CompleteMultipartUpload` with the list of part numbers + ETags.
Real S3 requires every part except the last to be **at least 5 MiB**. This module's
`MultipartUploader` implements that three-step protocol directly (rather than hiding
it behind a transfer-manager abstraction) specifically so you see the mechanics.

**Presigned URLs** let you grant temporary, credential-free access to a single
object: you sign a URL with your own credentials ahead of time, embedding an
expiry, and hand the URL to someone (or something) that has no AWS credentials
at all. This is the standard way to let a browser upload/download directly
to/from S3 without your backend proxying the bytes.

**Path-style vs. virtual-hosted-style addressing:** real AWS prefers
`https://bucket.s3.amazonaws.com/key` (virtual-hosted), but that requires DNS
that resolves `bucket.<host>`. Locally, this module uses **path-style**
(`http://localhost:4566/bucket/key`, via `forcePathStyle(true)`) since it needs
no special DNS setup — the same flag you'd flip for any S3-compatible storage
that doesn't do virtual-hosted routing.

**Object Lock** (WORM — write-once-read-many) is S3's mechanism for making
objects genuinely undeletable/unmodifiable for a retention period, used for
compliance data. Not exercised by this module's code, but worth knowing exists.

## What you'll build

**File Vault** — a small Spring Boot REST service backed by S3:

| Endpoint | What it does |
|---|---|
| `POST /api/files` (multipart) | Upload a file — routes through `MultipartUploader`, single-PUT or full multipart depending on size |
| `GET /api/files` | List everything in the bucket |
| `GET /api/files/{key}` | Download raw bytes |
| `GET /api/files/{key}/versions` | List every version ID stored for a key |
| `GET /api/files/{key}/presigned-url?expiry=PT15M` | Get a time-limited direct-access URL |
| `DELETE /api/files/{key}` | Delete the latest version |

`S3Config` creates the bucket (`task-tracker-files` — the same bucket
Module 01's IAM policy is scoped to) and turns on versioning at startup, so you
don't have to do it by hand.

Run it:

```bash
docker compose up -d
mvn -pl modules/02-s3 spring-boot:run
```

Then, in another terminal:

```bash
echo "hello floci" > /tmp/hello.txt
curl -F file=@/tmp/hello.txt http://localhost:8082/api/files
curl http://localhost:8082/api/files
curl http://localhost:8082/api/files/hello.txt
curl "http://localhost:8082/api/files/hello.txt/presigned-url"
```

## Tests

This module's test suite is a small demonstration of the testing pyramid itself
(the full version comes in Module 11):

```bash
mvn -pl modules/02-s3 test
```

- `MultipartUploaderTest` — pure unit test of the part-splitting math, no
  Spring, no S3, no Docker.
- `FileVaultControllerTest` — `@WebMvcTest` with `FileVaultService` mocked via
  `@MockitoBean`. Verifies routing and JSON shape without touching S3 at all.

```bash
mvn -pl modules/02-s3 test -Pfloci
```

- `FileVaultServiceIntegrationTest` — the real thing, against a disposable Floci
  instance: round-trips an upload/list/download/delete, proves versioning keeps
  every revision, forces a 6 MiB upload through the actual multipart protocol,
  and checks a presigned URL is well-formed.

## Checkpoint

- Why does `upload()` need to call `headObject` after writing, instead of just
  returning the key you passed in?
- What real S3 constraint does `MIN_PART_SIZE` in `MultipartUploader` encode,
  and what happens if you violate it against real AWS?
- If someone has only a presigned URL, what *can't* they do that someone with
  the actual role's credentials could?

## Next

[Module 03 — DynamoDB](../03-dynamodb) — build the Task Tracker's data layer,
the other resource Module 01's IAM policy is scoped to.
