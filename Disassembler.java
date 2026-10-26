
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Disassembler {
    // Sizes from the FizzBuzz program
    static final int TEXT_SIZE = 180;
    static final int DATA_SIZE = 10;
    static final int TEXT_START = 0x00400000;

    static final String[] registers = {
            "zero", "at", "v0", "v1", "a0", "a1", "a2", "a3",
            "t0", "t1", "t2", "t3", "t4", "t5", "t6", "t7",
            "s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7",
            "t8", "t9", "k0", "k1", "gp", "sp", "fp", "ra"
    };

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Disassembler fizzbuzz.mhc-ascii OR fizzbuzz.mhc-bin");
            return;
        }

        try {
            String file = args[0];
            byte[] bytes;
            String output;

            if (file.endsWith(".mhc-ascii")) {
                bytes = readAscii(file);
                output = "fizzbuzz.rebuilt-ascii.asm";
            } else if (file.endsWith(".mhc-bin")) {
                bytes = Files.readAllBytes(Paths.get(file));
                output = "fizzbuzz.rebuilt-bin.asm";
            } else {
                throw new IllegalArgumentException("File must end in .mhc-ascii or .mhc-bin");
            }

            if (bytes.length != TEXT_SIZE + DATA_SIZE) {
                throw new IllegalArgumentException("Wrong file size: " + bytes.length);
            }

            String source = makeAssembly(bytes);
            Assembler.Result check = Assembler.assemble(source);

            byte[] rebuilt = new byte[check.text.length + check.data.length];
            System.arraycopy(check.text, 0, rebuilt, 0, check.text.length);
            System.arraycopy(check.data, 0, rebuilt, check.text.length, check.data.length);

            if (!Arrays.equals(bytes, rebuilt)) {
                throw new IllegalArgumentException("Reassembled bytes do not match");
            }

            Files.write(Paths.get(output), source.getBytes(StandardCharsets.UTF_8));
            System.out.println("Created " + output + " - machine-code bytes match.");

        } catch (IOException | IllegalArgumentException e) {
            System.out.println("Disassembly failed: " + e.getMessage());
        }
    }

    static byte[] readAscii(String filename) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filename), StandardCharsets.US_ASCII);
        int instructions = TEXT_SIZE / 4;

        if (lines.size() != instructions + DATA_SIZE) {
            throw new IllegalArgumentException("Wrong number of lines in ASCII file");
        }

        byte[] bytes = new byte[TEXT_SIZE + DATA_SIZE];
        int n = 0;

        for (int i = 0; i < lines.size(); i++) {
            String bits = lines.get(i);
            int length = i < instructions ? 32 : 8;

            if (bits.length() != length || !bits.matches("[01]+")) {
                throw new IllegalArgumentException("Invalid binary on line " + (i + 1));
            }

            for (int j = 0; j < length; j += 8) {
                bytes[n++] = (byte) Integer.parseInt(bits.substring(j, j + 8), 2);
            }
        }

        return bytes;
    }

    static String makeAssembly(byte[] bytes) {
        int count = TEXT_SIZE / 4;
        int[] words = new int[count];
        Set<Integer> labels = new HashSet<>();

        labels.add(0);

        for (int i = 0; i < count; i++) {
            int p = i * 4;

            int word = ((bytes[p] & 255) << 24)
                    | ((bytes[p + 1] & 255) << 16)
                    | ((bytes[p + 2] & 255) << 8)
                    | (bytes[p + 3] & 255);

            words[i] = word;
            int op = word >>> 26;

            if (op == 2 || op == 3 || op == 4 || op == 5) {
                labels.add(jumpTarget(word, i, count));
            }
        }

        StringBuilder asm = new StringBuilder(".data\n");
        int pos = TEXT_SIZE;
        int number = 0;

        while (pos < bytes.length) {
            asm.append("str").append(number++).append(": .asciiz \"");
            boolean finished = false;

            while (pos < bytes.length) {
                int c = bytes[pos++] & 255;

                if (c == 0) {
                    finished = true;
                    break;
                }

                if (c == '"' || c == '\\') {
                    asm.append('\\');
                }

                if (c >= 32 && c <= 126) {
                    asm.append((char) c);
                } else if (c == 10) {
                    asm.append("\\n");
                } else if (c == 13) {
                    asm.append("\\r");
                } else if (c == 9) {
                    asm.append("\\t");
                } else {
                    throw new IllegalArgumentException("Unsupported data byte: " + c);
                }
            }

            if (!finished) {
                throw new IllegalArgumentException("Missing end of data string");
            }

            asm.append("\"\n");
        }

        asm.append("\n.text\n.globl main\n");

        for (int i = 0; i < count; i++) {
            if (labels.contains(i)) {
                asm.append(i == 0 ? "main:\n" : "L" + i + ":\n");
            }

            asm.append("    ").append(decode(words[i], i, count)).append('\n');
        }

        return asm.toString();
    }

    static int jumpTarget(int word, int i, int count) {
        int pc = TEXT_START + i * 4;
        int op = word >>> 26;
        int address;

        if (op == 2 || op == 3) {
            address = ((pc + 4) & 0xF0000000) | ((word & 0x03FFFFFF) << 2);
        } else {
            address = pc + 4 + ((short) word) * 4;
        }

        int offset = address - TEXT_START;

        if (offset < 0 || offset >= count * 4 || offset % 4 != 0) {
            throw new IllegalArgumentException("Jump or branch outside the text section");
        }

        return offset / 4;
    }

    static String label(int word, int i, int count) {
        int target = jumpTarget(word, i, count);
        return target == 0 ? "main" : "L" + target;
    }

    static String reg(int n) {
        return "$" + registers[n];
    }

    static String decode(int word, int i, int count) {
        int op = word >>> 26;
        int rs = (word >>> 21) & 31;
        int rt = (word >>> 16) & 31;
        int rd = (word >>> 11) & 31;
        int shift = (word >>> 6) & 31;
        int imm = (short) word;

        if (op == 0) {
            switch (word & 63) {
                case 0: return "sll " + reg(rd) + ", " + reg(rt) + ", " + shift;
                case 0x21: return "addu " + reg(rd) + ", " + reg(rs) + ", " + reg(rt);
                case 0x1A: return "div " + reg(rs) + ", " + reg(rt);
                case 0x10: return "mfhi " + reg(rd);
                case 8: return "jr " + reg(rs);
                case 12:
                    if (word == 12) return "syscall";
            }
        } else {
            switch (op) {
                case 8: return "addi " + reg(rt) + ", " + reg(rs) + ", " + imm;
                case 9: return "addiu " + reg(rt) + ", " + reg(rs) + ", " + imm;
                case 13: return "ori " + reg(rt) + ", " + reg(rs) + ", " + (word & 65535);
                case 15: return "lui " + reg(rt) + ", " + (word & 65535);
                case 4: return "beq " + reg(rs) + ", " + reg(rt) + ", " + label(word, i, count);
                case 5: return "bne " + reg(rs) + ", " + reg(rt) + ", " + label(word, i, count);
                case 2: return "j " + label(word, i, count);
                case 3: return "jal " + label(word, i, count);
                case 35: return "lw " + reg(rt) + ", " + imm + "(" + reg(rs) + ")";
                case 43: return "sw " + reg(rt) + ", " + imm + "(" + reg(rs) + ")";
            }
        }

        throw new IllegalArgumentException("Unsupported instruction at word " + (i + 1));
    }
}
