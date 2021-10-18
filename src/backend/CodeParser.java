package backend;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CodeParser {
    private final Map<String, String> supportedInstructions;
    private Map<String, Integer> labels = new HashMap<>();

    // for now let's keep the assembled code here
    // we'll use 2048 mem locations for code segment
    private short[] machineCode = new short[2048];

    public CodeParser() {
        supportedInstructions = FileParser.getSupportedInstructionsMap();
        Arrays.fill(machineCode, (short) 0);
    }

    public String parseCode(String code) {
        String errMessage = "Error on line number ";
        String[] codeLines = code.split("\n");

        try {
            purifyCode(codeLines);
        } catch (Exception e) {
            return errMessage + e.getMessage() + ", recurring label name";
        }

        int lineNumber = 1;
        int blankLinesCounter = 0;
        int memAddress = 0;
        String opcodeBinaryString;
        String argumentBinaryString;

        for (String line : codeLines) {
            if (line.equals("")) {
                blankLinesCounter++;
                lineNumber++;
                continue; // blank rows are allowed
            }

            String[] instructionElements = line.split(" ");

            if (!supportedInstructions.containsKey(instructionElements[0].toUpperCase()))
                return errMessage + lineNumber + ", unknown instruction mnemonic";

            argumentBinaryString = "00000000";

            if (instructionRequiresArgument(instructionElements[0])) {
                if (instructionElements.length < 2)
                    return errMessage + lineNumber + ", missing argument";;
                try {
                    if (instructionIsJump(instructionElements[0])) {
                        try {
                            Integer.parseInt(instructionElements[1]);
                        } catch (NumberFormatException ex) {
                            if (!labels.containsKey(instructionElements[1]))
                                return errMessage + lineNumber + ", unknown label name";
                            instructionElements[1] = String.valueOf(labels.get(instructionElements[1]));
                        }
                    }
                    int paramValue = Integer.parseInt(instructionElements[1]);
                    int boundary = 4095;
                    if (instructionElements[0].equalsIgnoreCase("INSP")
                            || instructionElements[0].equalsIgnoreCase("DESP"))
                        boundary = 255;
                    if (paramValue < 0 || paramValue > boundary)
                        return errMessage + lineNumber + ", argument out of bounds";

                    argumentBinaryString = Integer.toBinaryString(paramValue);

                } catch (NumberFormatException ex) {
                    return errMessage + lineNumber + ", invalid argument";
                }
                if (instructionElements.length > 2)
                    return errMessage + lineNumber + ", too many arguments";
            } else if (instructionElements.length > 1) {
                return errMessage + lineNumber + ", too many arguments";
            }

            opcodeBinaryString = supportedInstructions.get(instructionElements[0].toUpperCase());

            if (memAddress > 2047)
                return "Warning: Memory code segment is full, remaining instructions won't be assembled";
            else
                addInstructionToAssembledInstructions(memAddress, opcodeBinaryString, argumentBinaryString);

            lineNumber++;
            memAddress++;
        }
        if (blankLinesCounter == codeLines.length)
            return "Error blank code area";
        return "OK";
    }

    private void addInstructionToAssembledInstructions(int memAddress, String opcodeBinaryString, String argumentBinaryString) {
        String format = "%" + (16 - opcodeBinaryString.length()) + "s";
        argumentBinaryString = String.format(format, argumentBinaryString).replace(' ', '0');
        String binaryInstruction = opcodeBinaryString + argumentBinaryString;
        machineCode[memAddress] = (short) Integer.parseInt(binaryInstruction, 2);
    }

    private boolean instructionIsJump(String mnemonic) {
        return mnemonic.equalsIgnoreCase("JPOS") || mnemonic.equalsIgnoreCase("JZER")
                || mnemonic.equalsIgnoreCase("JUMP") || mnemonic.equalsIgnoreCase("JNEG")
                || mnemonic.equalsIgnoreCase("JNZE");
    }

    // As I couldn't think of a better name
    // This method removes all labels and comments from code, and removes all unnecessary blanks
    private void purifyCode(String[] codeLines) throws Exception {
        int memoryAddress = 0;
        for (int i = 0; i < codeLines.length; i++) {
            codeLines[i] = codeLines[i].replaceAll(";.*", ""); // remove comments
            codeLines[i] = codeLines[i].trim(); // get rid of all leading and trailing blanks
            codeLines[i] = codeLines[i].replaceAll(" +", " "); // replace multiple blanks with one

            if (codeLines[i].equals("")) continue;

            String[] elements = codeLines[i].split(" ");
            if (elements.length >= 1 && elements[0].endsWith(":")) {
                String newLabel = elements[0].substring(0, elements[0].length()-1);
                if (labels.containsKey(newLabel))   // recurring label
                    throw new Exception(String.valueOf(i+1));

                labels.put(newLabel, memoryAddress);

                // get rid of label
                // we don't increment the mem address, bcs this line is only a label
                if (elements.length > 1) {
                    codeLines[i] = codeLines[i].replace(newLabel + ": ", "");
                    i--;    // if there are multiple labels on one line
                }
                else {
                    codeLines[i] = codeLines[i].replace(newLabel + ":", "");
                }
                continue;
            }
            memoryAddress++;
        }
    }

    private boolean instructionRequiresArgument(String mnemonic) {
        return !(mnemonic.equals("PSHI") || mnemonic.equals("POPI") || mnemonic.equals("PUSH")
                || mnemonic.equals("POP") || mnemonic.equals("RETN") || mnemonic.equals("SWAP"));
    }

    public Map<String, Integer> getLabels() {
        return labels;
    }

    public short[] getMachineCode() {
        return machineCode;
    }
}
