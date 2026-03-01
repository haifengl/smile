/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.tool;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import smile.data.Tuple;
import smile.io.Parquet;

@JsonClassDescription("""
Reads a file from the local filesystem. You can access any file directly by using this tool.
Assume this tool is able to read all files on the machine. If the User provides a path to a file assume that path is valid. It is okay to read a file that does not exist; an error will be returned.

Usage:
- The file_path parameter must be an absolute path, not a relative path
- By default, it reads up to 2000 lines starting from the beginning of the file
- You can optionally specify a line offset and limit (especially handy for long files), but it's recommended to read the whole file by not providing these parameters
- Any lines longer than 2000 characters will be truncated
- Results are returned using cat -n format, with line numbers starting at 1
- This tool can read Parquet files (.parquet). When reading Parquet files, the output will be in CSV format with a header row.
- This tool can only read files, not directories. To read a directory, use an ls command via the Bash tool.
- You can call multiple tools in a single response. It is always better to speculatively read multiple potentially useful files in parallel.
- You will regularly be asked to read screenshots. If the user provides a path to a screenshot, ALWAYS use this tool to view the file at the path. This tool will work with all temporary file paths.
- If you read a file that exists but has empty contents you will receive a system reminder warning in place of file contents.
""")
public class Read implements Tool {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The absolute path to the file to read")
    public String filePath;

    @JsonPropertyDescription("The line number to start reading from. Only provide if the file is too large to read at once.")
    public int offset = 0;

    @JsonPropertyDescription("The number of lines to read. Only provide if the file is too large to read at once.")
    public int limit = 2000;

    @Override
    public String run() {
        return readFile(filePath, offset, limit);
    }

    /** Static helper method to read a file. */
    public static String readFile(String filePath, int offset, int limit) {
        var path = Path.of(filePath);
        if (Files.exists(path)) {
            try {
                if (path.getFileName().endsWith(".parquet")) {
                    var df = Parquet.read(path, offset + limit);
                    int p = df.schema().length();
                    String[] header = new String[p];
                    for (int i = 0; i < p; i++) {
                        header[i] = df.schema().field(i).name();
                    }

                    CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(header).get();
                    try (StringWriter writer = new StringWriter();
                         CSVPrinter printer = new CSVPrinter(writer, format)) {
                        List<String> record = new ArrayList<>(p);
                        for (int i = 0; i < limit; i++) {
                            Tuple row = df.get(offset + i);
                            for (int j = 0; j < p; j++) {
                                record.add(row.getString(j));
                            }
                            printer.printRecord(record);
                            record.clear();
                        }
                        printer.flush();
                        return writer.toString();
                    }
                } else {
                    AtomicInteger index = new AtomicInteger(offset);
                    return Files.lines(path)
                            .skip(offset)
                            .limit(limit)
                            .map(line -> String.format("%6d\t%s", index.incrementAndGet(),
                                    line.length() > 2000 ? line.substring(0, 2000) : line))
                            .collect(Collectors.joining("\n"));
                }
            } catch (Exception e) {
                return String.format("Failed to read file '%s': %s", filePath, e.getMessage());
            }
        }

        return String.format("Error: File '%s' does not exist.", filePath);
    }
}
