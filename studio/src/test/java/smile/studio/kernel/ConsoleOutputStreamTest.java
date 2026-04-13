package smile.studio.kernel;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
public class ConsoleOutputStreamTest {
    private ConsoleOutputStream stream;
    @BeforeEach
    public void setUp() { stream = new ConsoleOutputStream(); }
    @AfterEach
    public void tearDown() throws Exception { stream.close(); }
    @Test
    public void testWriteByteNoAreaIsNoOp() {
        assertDoesNotThrow(() -> stream.write('A'));
    }
    @Test
    public void testWriteBytesNoAreaIsNoOp() {
        assertDoesNotThrow(() -> stream.write("hello".getBytes(StandardCharsets.UTF_8), 0, 5));
    }
    @Test
    public void testFlushNoAreaIsNoOp() {
        assertDoesNotThrow(() -> stream.flush());
    }
    @Test
    public void testGetOutputAreaNullByDefault() {
        assertNull(stream.getOutputArea());
    }
    @Test
    public void testRemoveOutputAreaSetsNull() {
        stream.removeOutputArea();
        assertNull(stream.getOutputArea());
    }
    @Test
    public void testWriteBytesHandlesUTF8Multibyte() {
        byte[] bytes = "\uD83D\uDE00".getBytes(StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> stream.write(bytes, 0, bytes.length));
    }
    @Test
    public void testPrintStreamDoesNotThrow() {
        PrintStream ps = new PrintStream(stream, true, StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> { ps.println("line 1"); ps.flush(); });
    }
    @Test
    public void testWriteSingleByteRange() {
        assertDoesNotThrow(() -> {
            for (int c = 32; c < 127; c++) stream.write(c);
        });
    }
}