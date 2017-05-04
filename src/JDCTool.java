import com.jcloud.jss.Credential;
import com.jcloud.jss.JingdongStorageService;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class JDCTool {
    private JingdongStorageService jss = null;
    private Options opts = null;

    public static void main(String args[]) {
        JDCTool tool = new JDCTool();
        tool.initOptions();

        Map<String, String> options = tool.getOptions(args);
        tool.initJSS(options);
        tool.execute(options);
    }

    /**
     * Execute the job given by command line.
     * @param options
     */
    private void execute(Map<String, String> options) {
        if (options.get("bucket") == null) {
            System.out.println("Must specify bucket for upload.");
            return;
        }

        if (options.get("upload") != null) {
            upload(options);
        } else if (options.get("list-bucket") != null) {
            System.out.println(jss.listBucket().toString());
        }

        else {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("jdctool [options]", opts);
        }
    }

    /**
     * Upload the files in given path. Retry one time for failed connection.
     * @param options
     */
    private void upload(Map<String, String> options) {
        System.out.println("Uploading...");

        String[] dest = options.get("upload").split(" ");

        List<String> files = getAllFilePathsUnder(options.get("upload"));

        // TODO: check the remote list to bypass existing files.

        List<String> failList = doUpload(options, files);

        int retry = 1;
        while (failList.size() > 0 && retry-->0) {
            System.out.println("Retry one time for fail list: ");
            System.out.println(failList);
            failList = doUpload(options, failList);
        }

        if (failList.size()>0) {
            System.out.println("Fail list: ");
            System.out.println(failList);
        }
    }

    /**
     * Actual upload action for the list of the files in path.
     * @param options
     * @param files
     * @return
     */
    private List<String> doUpload(Map<String, String> options, List<String> files) {
        boolean overwrite = !options.get("overwrite").isEmpty();
        String bucket = options.get("bucket");
        List<String> failList = new ArrayList<>();

        int size = files.size(), success=0, fail=0;
        for (String path : files) {
            try {
                if (!overwrite && jss.bucket(bucket).object(path).exist()) {
                    success++;
                } else {
                    String mime = Files.probeContentType(Paths.get(path));
                    String ret = jss.bucket(bucket).object(path).entity(new File(path)).contentType(mime).put();
                    String md5 = getMD5(path);
                    if (md5.equals(ret)) success++;
                }

                System.out.print(String.format("\rSuccess/Failed/Total: %d/%d/%d\n\n", success, fail, size));

            } catch (UnknownHostException e) {
                e.printStackTrace();
                break;
            } catch (NullPointerException e) {
                // File is empty possibly, bypass.
                e.printStackTrace();
            } catch (RuntimeException | IOException | NoSuchAlgorithmException e) {
                e.printStackTrace();
                fail++;
                failList.add(path);
            }
        }

        System.out.print(String.format("\r\nSuccess/Failed/Total: %d/%d/%d\n", success, fail, size));
        return failList;
    }

    /**
     * Compute the md5 for specific file by given the path.
     * @param str
     * @return
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    private static String getMD5(String str) throws NoSuchAlgorithmException, IOException {
        File file = new File(str);
        FileInputStream in = new FileInputStream(file);
        MappedByteBuffer byteBuffer = in.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, file.length());
        // 生成一个MD5加密计算摘要
        MessageDigest md = MessageDigest.getInstance("MD5");
        // 计算md5函数
        md.update(byteBuffer);
        // digest()最后确定返回md5 hash值，返回值为8为字符串。因为md5 hash值是16位的hex值，实际上就是8位的字符
        // BigInteger函数则将8位的字符串转换成16位hex值，用字符串来表示；得到字符串形式的hash值
        return new BigInteger(1, md.digest()).toString(16);
    }

    /**
     * Initialize the option candidates for command line.
     */
    private void initOptions() {
        opts = new Options();
        opts.addOption("akey","accesskey", true,"Access key for jcloud.");
        opts.addOption("skey","secretkey", true,"Secret key for jcloud.");
        opts.addOption("ep","endpoint", true,"Endpoint is a specific data center address in which it connects to.");
        opts.addOption("b","bucket", true,"Bucket in which the data is saved to.");
        opts.addOption("la","list-acl", false,"List acl for specific bucket. Use with -bucket");
        opts.addOption("sa","set-acl", true,"set acl for specific bucket. Use with -bucket");
        opts.addOption("lb","list-bucket", false,"List available buckets.");
        opts.addOption("u","upload", true,"Mapping local to remote in dirs and files.");
        opts.addOption("o","overwrite", false,"Indicate if upload will overwrite existing file. Use with -upload.");
    }

    /**
     * Get the argument list of command line.
     * @param args
     * @return
     */
    private Map<String, String> getOptions(String args[]) {
        Map<String, String> option = new HashMap<>();
        CommandLineParser parser = new DefaultParser();

        try {
            CommandLine cmdline = parser.parse(opts, args);
            option.put("accesskey", cmdline.getOptionValue("accesskey", "1DA8D2A50D57A2DFB3CD13AA70C0F460"));
            option.put("secretkey", cmdline.getOptionValue("secretkey", "78EA48466AF2B5A77B2481274F10A4CF"));
            option.put("endpoint", cmdline.getOptionValue("endpoint", "s-bj.jcloud.com"));
            option.put("bucket", cmdline.getOptionValue("bucket", "tripitaka"));
            option.put("upload", cmdline.getOptionValue("upload", null));
            option.put("overwrite", cmdline.hasOption("overwrite")? "Not Empty":"");

        } catch (ParseException e) {
            e.printStackTrace();
        }

        return option;
    }

    /**
     * Initialize Jingdong storage service.
     * @param opt
     */
    private void initJSS(Map<String, String> opt) {
        String accesskey = opt.get("accesskey");
        String secretkey = opt.get("secretkey");
        String endpoint = opt.get("endpoint");
        String bucket = opt.get("bucket");

        Credential credential = new Credential(accesskey, secretkey);
        jss = new JingdongStorageService(credential);
        jss.setEndpoint(endpoint); // Set end point to the data center in the north of China

        if (!jss.hasBucket(bucket)) {
            jss.bucket(bucket).create();
            jss.bucket(bucket).acl().internetVisible().set();
        }
    }

    /**
     * Get all files' path under given dir.
     * @param path
     * @return
     */
    private List<String> getAllFilePathsUnder(String path) {
        File file = new File(path);
        List<String> fileList = new ArrayList<>();
        if (file.exists()) {
            File[] files = file.listFiles();
            if(files == null) {
                if (file.isFile()) fileList.add(file.getPath());
                return fileList;
            }

            // First depth list
            LinkedList<File> dirs = new LinkedList<File>();
            for (File file2 : files) {
                if (file2.isDirectory()) {
                    dirs.add(file2);
                } else {
                    fileList.add(file2.getPath());
                }
            }

            // Recursively fetch all files under dirs
            File temp_file;
            while (!dirs.isEmpty()) {
                temp_file = dirs.removeFirst();
                files = temp_file.listFiles();
                if(files == null) continue;

                for (File file2 : files) {
                    if (file2.isDirectory()) {
                        dirs.add(file2);
                    } else {
                        fileList.add(file2.getPath());
                    }
                }
            }
        }

        return fileList;
    }
}

