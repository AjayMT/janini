import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonArray;
import org.apache.commons.cli.*;
import spark.Filter;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

import static spark.Spark.*;

/**
 * A small web server that runs arbitrary Java code.
 */
public class WebServer {
    /**
     * The port that our server listens on.
     */
    private static final int DEFAULT_SERVER_PORT = 8888;

    /**
     * Default path to test files.
     */
    private static String testPath = "./question-tests/";

    /**
     * Cached test sources.
     */
    private static Map<String, String> cachedTests = new HashMap<>();

    static {
        System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog");
        System.setProperty("org.eclipse.jetty.LEVEL", "OFF");
    }

    /**
     * Construct a Source object from a JSON request.
     *
     * @param requestBody request content as a String
     * @param withTests whether to add test suite code to the source
     * @return the Source object
     */
    private static Source getSource(final String requestBody, final boolean withTests) {
        JsonObject requestContent = Json.parse(requestBody).asObject();
        String runAs;
        if (requestContent.get("as") != null) {
            runAs = requestContent.get("as").asString();
        } else {
            runAs = "Snippet";
        }
        if (withTests && requestContent.get("runTests") != null
            && requestContent.get("testClassName") != null) {
            boolean runTests = requestContent.get("runTests").asBoolean();
            String testClassName = requestContent.get("testClassName").asString();
            if (runTests) {
                String testSource = "";
                if (cachedTests.get(testClassName) != null) {
                    testSource = cachedTests.get(testClassName);
                } else {
                    try {
                        testSource = new String(Files.readAllBytes(Paths.get(testPath, testClassName + ".java")));
                        cachedTests.put(testClassName, testSource);
                    } catch (Exception e) {
                        System.err.println(e.toString());
                    }
                }
                JsonArray sources = requestContent.get("sources").asArray();
                sources.add(testSource);
                requestContent.remove("sources");
                requestContent.add("sources", sources);
            }
        }
        Source source = null;
        switch (runAs) {
        case "Snippet":
            source = Source.received(requestContent.toString(), Snippet.class);
            break;
        case "SimpleCompiler":
            source = Source.received(requestContent.toString(), SimpleCompiler.class);
            break;
        default:
            break;
        }

        return source;
    }

    /**
     * Run submitted code.
     * <p>
     * Exposed here for use by the testing suite.
     *
     * @param requestBody request content as a String
     * @return response as a String
     */
    public static String run(final String requestBody) {
        Source source = getSource(requestBody, false);
        if (source == null) {
            return requestBody;
        }
        if (source instanceof SimpleCompiler) {
            ((SimpleCompiler) source).runTests = false;
        }
        return source.compile().execute().completed();
    }

    /**
     * Run tests on submitted code.
     * <p>
     * Exposed here for use by the testing suite.
     *
     * @param requestBody request content as a String
     * @return response as a String
     */
    public static String test(final String requestBody) {
        Source source = getSource(requestBody, true);
        if (source == null) {
            return requestBody;
        }
        source = source.compile().execute();
        SimpleCompiler executed = (SimpleCompiler) source;
        // TODO find a better way to remove test code
        // or find a better way to run tests
        executed.sources = Arrays.copyOf(executed.sources, executed.sources.length - 1);
        return source.completed();
    }

    /**
     * Run checkstyle on submitted code.
     * <p>
     * Exposed here for use by the testing suite.
     *
     * @param requestBody request content as a String
     * @return response as a String
     */
    public static String checkstyle(final String requestBody) {
        Source source = getSource(requestBody, false);
        if (source == null) {
            return requestBody;
        }
        source.runCheckstyle = true;
        source.requireCheckstyle = true;
        return source.checkstyle().completed();
    }

    /**
     * Start the code execution web server.
     *
     * @param args command line arguments
     * @throws ParseException thrown if command line options cannot be parsed
     */
    public static void main(final String[] args) throws ParseException {

        Options options = new Options();
        options.addOption("p", "port", true, "Port to use. Default is 8888.");
        options.addOption("i", "interactive", false, "Enable interactive mode.");
        options.addOption("l", "local", false, "Enable local development mode by disabling CORS.");
        options.addOption("v", "verbose", false, "Enable verbose mode.");
        options.addOption("c", "checkstyle", true,
                          "Path to checkstyle configuration file. Defaults to ./defaults/checkstyle.xml");
        options.addOption("t", "tests", true, "Path to test files. Defaults to ./question-tests/");
        CommandLineParser parser = new BasicParser();
        CommandLine settings = parser.parse(options, args);

        if (settings.getOptionValue("t") != null) {
            testPath = settings.getOptionValue("t");
        }
        File testDir = new File(testPath);
        File[] testFiles = testDir.listFiles();
        for (File f : testFiles) {
            String testClassName = f.getName().split("\\.java")[0];
            Path testSourcePath = Paths.get(testPath, f.getName());
            String testSource = "";
            try {
                testSource = new String(Files.readAllBytes(testSourcePath));
            } catch (Exception e) {
                System.err.println(e);
            }
            cachedTests.put(testClassName, testSource);
        }

        if (settings.hasOption("p")) {
            port(Integer.parseInt(settings.getOptionValue("p")));
        } else {
            port(DEFAULT_SERVER_PORT);
        }

        if (settings.hasOption("i")) {
            staticFiles.location("/webroot");
        }

        try {
            Source.initialize(settings);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

        post("/run", (request, response) -> {
                try {
                    response.type("application/json; charset=utf-8");
                    return run(request.body());
                } catch (Exception e) {
                    if (settings.hasOption("v")) {
                        System.err.println(e.toString());
                    }
                    return "";
                }
            });

        post("/test", (request, response) -> {
                try {
                    response.type("application/json; charset=utf-8");
                    return test(request.body());
                } catch (Exception e) {
                    if (settings.hasOption("v")) {
                        System.err.println(e.toString());
                    }
                    return "";
                }
            });

        post("/checkstyle", (request, response) -> {
                try {
                    response.type("application/json; charset=utf-8");
                    return checkstyle(request.body());
                } catch (Exception e) {
                    if (settings.hasOption("v")) {
                        System.err.println(e.toString());
                    }
                    return "";
                }
            });

        if (settings.hasOption("l")) {
            after((Filter) (request, response) -> {
                    response.header("Access-Control-Allow-Origin", "*");
                    response.header("Access-Control-Allow-Methods", "POST,GET");
                });
        }
    }
}
