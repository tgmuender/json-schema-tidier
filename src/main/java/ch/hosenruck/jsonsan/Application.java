package ch.hosenruck.jsonsan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import io.vavr.Tuple;
import io.vavr.Tuple2;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 *
 */
public class Application {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) {
        checkArguments(args);

        Stream.of(args)
                .map(arg -> Paths.get(arg))
                .map(path -> readSchema(path))
                .map(t -> new Sanitizer(t))
                .forEach(
                        jst -> writeSanitized(
                                jst.getOriginalSchemaLocation(),
                                jst.unnestInlinedObjects()
                        )
                );
    }

    private static void checkArguments(String[] args) {
        if (args == null || args.length == 0) {
            System.err.println("Please provide full path to JSON schema file as argument");
            System.exit(1);
        }
    }

    private static void writeSanitized(Path originalSchemaLocation, JsonObject jsonObject) {
        if (jsonObject == null) {
            return;
        }
        final String prettySchema = GSON.toJson(jsonObject);
        System.out.println(prettySchema);
        final Path schemaOutputLocation = originalSchemaLocation.resolveSibling(originalSchemaLocation.getFileName() + ".san");
        try {
            Files.write(schemaOutputLocation, prettySchema.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Tuple2<Path, JsonObject> readSchema(Path path) {
        final JsonReader reader;
        try {
            reader = new JsonReader(new FileReader(path.toFile()));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return Tuple.of(path, null);
        }
        return Tuple.of(
                path,
                GSON.fromJson(reader, JsonObject.class)
        );
    }

}
