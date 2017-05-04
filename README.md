## Usage
```
$ java -jar jdctool.jar

usage: jdctool [options]
 -akey,--accesskey <arg>   Access key for jcloud.
 -b,--bucket <arg>         Bucket in which the data is saved to.
 -ep,--endpoint <arg>      Endpoint is a specific data center address in
                           which it connects to.
 -la,--list-acl            List acl for specific bucket. Use with -bucket
 -lb,--list-bucket         List available buckets.
 -o,--overwrite            Indicate if upload will overwrite existing
                           file. Use with -upload.
 -sa,--set-acl <arg>       set acl for specific bucket. Use with -bucket
 -skey,--secretkey <arg>   Secret key for jcloud.
 -u,--upload <arg>         Mapping local to remote in dirs and files.
```

```
java -jar jdctool.jar -upload some/path/to/file
```
