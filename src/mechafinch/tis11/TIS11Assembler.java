package mechafinch.tis11;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Command Line tool for assembling TIS-11 programs
 * 
 * @author Mechafinch
 */
public class TIS11Assembler {
	
	/*
	 * Usage
	 * TIS11Assembler [-v] inputfile [outputfile]
	 * 	outputfile: The file to output to
	 * 	inputfile: The file to take input from
	 *  -v: Verbose output
	 */
	public static void main(String[] args) {
		boolean verbose = false;
		
		// Sanitize arguments, enabling verbose output if necessary and constructing the output destination if necessary
		if(args.length > 0) {
			if(args[0].toLowerCase().equals("-v")) {
				if(args.length < 2) {
					printUsage("Missing arguments");
					return;
				} else if(args.length > 3) {
					printUsage("Too many arguments");
					return;
				}
				
				if(args.length == 3) {
					args = new String[] {args[1], args[2]};
				} else {
					args = new String[] {args[1]};
				}
				
				verbose = true;
			}
			
			if(args.length > 2) {
				printUsage("Too many arguments");
				return;
			} else if(args.length == 1) {
				args = new String[] {args[0], args[0].substring(0, args[0].indexOf('.')) + "_bin.txt"};
			}
		} else {
			printUsage("Missing arguments");
			return;
		}
		
		String inputFilePath = args[0],
			   outputFilePath = args[1];
		
		// Echo arguments
		if(verbose) System.out.println("Input File: " + inputFilePath + "\nOutput File: " + outputFilePath + "\n");
		
		// Read the file
		String[] inputLines;
		try {
			inputLines = readInputFile(inputFilePath);
		} catch(IOException e) {
			System.err.println(e.getMessage());
			return;
		}
		
		/*
		// Echo input file
		for(String s : inputLines) {
			System.out.println(s);
		}
		*/
		
		// Sanitize input file
		if(inputLines.length == 0) {
			System.err.println("Empty input file");
			return;
		}
		
		for(int i = 0; i < inputLines.length; i++) inputLines[i] = inputLines[i].toUpperCase();
		
		// A list of lists of strings, where each list holds the program of a node, along with its id
		ArrayList<ArrayList<String>> nodes = new ArrayList<>();
		
		// Determine file format, run proper parser
		if(inputLines[0].startsWith("@")) { // Game save file
			nodes = separateGameFormat(inputLines);
		} else if(inputLines[0].startsWith("<<")) { // Node-map format
			nodes = separateNMFormat(inputLines);
		} else {
			System.err.println("Improper file format");
			return;
		}
		
		// Print node list
		if(verbose) nodes.forEach(n -> n.forEach(System.out::println));
		
		// Object holding the output
		ArrayList<ArrayList<String>> binaries = new ArrayList<>();
		
		// Some common binary strings
		String zeroSource = "000000000000",
			   zeroDest = "00000";
		
		// Assemble into binary
		for(ArrayList<String> node : nodes) {
			ArrayList<String> bin = new ArrayList<>();
			bin.add(node.get(0)); // Header line
			String nodeCoords = String.format("(%s)", node.get(0).substring(0, node.get(0).lastIndexOf(',')));
			
			// Parsing time! First, pass over and collect labels, removing them from the strings and generally cleaning
			HashMap<String, Integer> labelMap = new HashMap<>();
			for(int i = 1; i < node.size(); i++) {
				String s = node.get(i);
				if(s.contains(":")) {
					String l = s.substring(0, s.indexOf(':'));
					
					if(labelMap.containsKey(l)) {
						printError("Duplicate label '%s'", nodeCoords, i - 1, l);
						return;
					}
					
					labelMap.put(l, i - 1);
					s = s.substring(s.indexOf(':') + 1);
				}
				
				// Clean up the instruction, removing leading whitespace and comments
				if(s.length() == 0) { // Remove empty lines (we'll check this again)
					node.remove(i--);
					continue;
				}
				
				while(Character.isWhitespace(s.charAt(0))) s = s.substring(1);	// Remove leading whitespace
				if(s.contains("#")) s = s.substring(0, s.indexOf('#'));			// Remove comments
				
				if(s.length() == 0) { // Check if empty again, cause we've removed stuff
					node.remove(i--);
					continue;
				}
				
				node.set(i, s);
			}
			
			// Make sure we fit within the 15 instructions, now that we're only instructions
			if(node.size() > 15) {
				System.err.println(String.format("Error at %s: Too many instructions", nodeCoords));
				return;
			}
			
			// We have pure opcodes now. Next we pass over and parse things
			for(int i = 1; i < node.size(); i++) {
				String[] instruction = node.get(i).split(" ");
				if(instruction.length == 0) { // This should have been cleaned but we'll check anyways
					printError("Empty instruction", nodeCoords, i - 1);
					return;
				}
				
				// Determine opcode, convert to binary in each
				try { // Catch errors from parsing methods
					if(instruction[0].equals("NOP")) {				// NOP
						bin.add("0000" + zeroSource + zeroDest);
					} else if(instruction[0].equals("MOV")) {		// MOV
						// Verify we have source and destination
						if(instruction.length != 3) {
							if(instruction.length < 3) printError("Missing arguments", nodeCoords, i - 1);
							else printError("Too many arguments", nodeCoords, i - 1);
							return;
						}
						
						bin.add("0001" + parseSource(instruction[1], nodeCoords, i - 1) + parseDestination(instruction[2], nodeCoords, i - 1));
					} else if(instruction[0].equals("SWP")) {		// SWP
						bin.add("0010" + zeroSource + zeroDest);
					} else if(instruction[0].equals("SAV")) {		// SAV
						bin.add("0011" + zeroSource + zeroDest);
					} else if(instruction[0].equals("ADD")) {		// ADD
						// Verify arguments
						if(instruction.length != 2) {
							if(instruction.length < 2) printError("Missing arguments", nodeCoords, i - 1);
							else printError("Too many arguments", nodeCoords, i - 1);
							return;
						}
						
						bin.add("0100" + parseSource(instruction[1], nodeCoords, i - 1) + zeroDest);
					} else if(instruction[0].equals("SUB")) {		// SUB
						// Verify args
						if(instruction.length != 2) {
							if(instruction.length < 2) printError("Missing arguments", nodeCoords, i - 1);
							else printError("Too many arguments", nodeCoords, i - 1);
							return;
						}
						
						bin.add("0101" + parseSource(instruction[1], nodeCoords, i - 1) + zeroDest);
					} else if(instruction[0].equals("NEG")) {		// NEG
						bin.add("0110" + zeroSource + zeroDest);
					} else if(instruction[0].equals("JMP")) {		// JMP
						// Verify args
						if(instruction.length != 2) {
							if(instruction.length < 2) printError("Missing argmuents", nodeCoords, i - 1);
							else printError("Too many arguments", nodeCoords, i - 1);
							return;
						}
						
						bin.add("1000" + zeroSource + parseLabel(instruction[1], labelMap, nodeCoords, i - 1));
					} else if(instruction[0].equals("JEZ")) {		// JEZ
						// Verify args
						if(instruction.length != 2) {
							if(instruction.length < 2) printError("Missing argmuents", nodeCoords, i - 1);
							else printError("Too many arguments", nodeCoords, i - 1);
							return;
						}
						
						bin.add("1001" + zeroSource + parseLabel(instruction[1], labelMap, nodeCoords, i - 1));
					} else if(instruction[0].equals("JNZ")) {		// JNZ
						// Verify args
						if(instruction.length != 2) {
							if(instruction.length < 2) printError("Missing argmuents", nodeCoords, i - 1);
							else printError("Too many arguments", nodeCoords, i - 1);
							return;
						}
						
						bin.add("1010" + zeroSource + parseLabel(instruction[1], labelMap, nodeCoords, i - 1));
					} else if(instruction[0].equals("JGZ")) {		// JGZ
						// Verify args
						if(instruction.length != 2) {
							if(instruction.length < 2) printError("Missing argmuents", nodeCoords, i - 1);
							else printError("Too many arguments", nodeCoords, i - 1);
							return;
						}
						
						bin.add("1011" + zeroSource + parseLabel(instruction[1], labelMap, nodeCoords, i - 1));
					} else if(instruction[0].equals("JLZ")) {		// JLZ
						// Verify args
						if(instruction.length != 2) {
							if(instruction.length < 2) printError("Missing argmuents", nodeCoords, i - 1);
							else printError("Too many arguments", nodeCoords, i - 1);
							return;
						}
						
						bin.add("1100" + zeroSource + parseLabel(instruction[1], labelMap, nodeCoords, i - 1));
					} else if(instruction[0].equals("JRO")) {		// JRO
						// Verify args
						if(instruction.length != 2) {
							if(instruction.length < 2) printError("Missing arguments", nodeCoords, i - 1);
							else printError("Too many arguments", nodeCoords, i - 1);
							return;
						}
						
						bin.add("1101" + parseSource(instruction[1], nodeCoords, i - 1) + zeroDest);
					} else if(instruction[0].equals("HCF")) {		// HCF
						bin.add("1111" + zeroSource + zeroDest);
					} else { // Invalid opcode
						printError("Invalid opcode '%s'", nodeCoords, i - 1, instruction);
						return;
					}
				} catch(ParseException e) {
					System.err.println(e.getMessage());
					return;
				}
			}
			
			binaries.add(bin);
		}
		
		if(verbose) {
			System.out.println("\n");
			binaries.forEach(n -> n.forEach(System.out::println));
		}
		
		// Write the output to a file
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(outputFilePath));
			
			// Loop over assembled nodes
			for(ArrayList<String> node : binaries) {
				bw.write(node.get(0)); // Output node title
				bw.newLine();
				
				// Output each line of binary, with its number
				for(int i = 1; i < node.size(); i++) {
					bw.write((i - 1) + ": " + node.get(i));
					bw.newLine();
				}
				
				bw.newLine(); // Empty line between nodes
			}
			
			bw.close();
		} catch(IOException e) {
			System.err.println(e.getMessage());
			return;
		}
		
		System.out.println("\nAssembly complete. Output written to " + outputFilePath + "\n");

	}
	
	/**
	 * Parses a label into a binary string
	 * 
	 * @param lbl The label
	 * @param lblMap The hashmap of labels and their values
	 * @param coords The coordinates of the node
	 * @param line The line of code
	 * @return A binary string
	 * @throws ParseException
	 */
	private static String parseLabel(String lbl, HashMap<String, Integer> lblMap, String coords, int line) throws ParseException {
		if(!lblMap.containsKey(lbl)) {
			throw new ParseException("Label not found '%s'", coords, line, lbl);
		}
		
		String binString = Integer.toBinaryString(lblMap.get(lbl));		
		return "0" + String.format("%4s", binString).replace(' ', '0');
	}
	
	/**
	 * Parses a destination into a binary string
	 * 
	 * @param dst The destination
	 * @param coords The coordinates of the node
	 * @param line The line of code
	 * @return A binary string
	 * @throws ParseException
	 */
	private static String parseDestination(String dst, String coords, int line) throws ParseException {
		switch(dst) { // Registers and ports
			case "NIL":
				return "10000";
			
			case "ACC":
				return "10001";
			
			case "LEFT":
				return "11000";
			
			case "RIGHT":
				return "11001";
				
			case "UP":
				return "11010";
				
			case "DOWN":
				return "11011";
				
			case "ANY":
				return "11100";
				
			case "LAST":
				return "11101";
		}
		
		// Cannot be anything else
		throw new ParseException("Invalid destination", coords, line);
	}
	
	/**
	 * Parses a source into a binary string
	 * 
	 * @param src The source
	 * @param coords The coordinates of the node
	 * @param line The line of code
	 * @return A binary string
	 * @throws ParseException
	 */
	private static String parseSource(String src, String coords, int line) throws ParseException {
		switch(src) { // Registers and ports
			case "NIL":
				return "100000000000";
			
			case "ACC":
				return "100000000001";
			
			case "LEFT":
				return "110000000000";
			
			case "RIGHT":
				return "110000000001";
				
			case "UP":
				return "110000000010";
				
			case "DOWN":
				return "110000000011";
				
			case "ANY":
				return "110000000100";
				
			case "LAST":
				return "110000000101";
		}
		
		// Must be immediate value
		try {
			int imm = Integer.parseInt(src);
			
			if(imm < -1024 || imm > 1023) throw new ParseException("Invalid source (immediate out of range)", coords, line);
			
			String binaryString = String.format("%11s", Integer.toBinaryString(imm)).replace(' ', '0');
			if(binaryString.length() > 11) binaryString = binaryString.substring(binaryString.length() - 11);
			
			return "0" + binaryString;
		} catch(NumberFormatException e) {
			throw new ParseException("Invalid source (not a number or register)", coords, line);
		}
	}
	
	/**
	 * Prints a parsing error
	 * 
	 * @param msg The error message
	 * @param coords The coordinates of the node
	 * @param line The line of code that caused the error
	 * @param others Other information
	 */
	private static void printError(String msg, String coords, int line, String ... others) {
		Object[] args = new Object[others.length + 2];
		args[0] = coords;
		args[1] = line;
		
		for(int i = 0; i < others.length; i++) args[i + 2] = others[i];
		
		System.err.println(String.format("Error at %s, line %d: " + msg, args));
	}
	
	/**
	 * Separates the raw programs of each node from a game save file
	 * 
	 * @param input The input file
	 * @return A list of node program lists
	 */
	private static ArrayList<ArrayList<String>> separateGameFormat(String[] input) {
		ArrayList<ArrayList<String>> nodeList = new ArrayList<>();
		
		// Loop over lines
		for(int i = 0;;) {
			// List representing the node
			ArrayList<String> node = new ArrayList<>();
			
			// Go until we find the next node
			while(!input[i].startsWith("@")) i++;
			
			// Grab its location, assume all nodes available
			int id = Integer.parseInt(input[i].substring(1));
			node.add(String.format("%s,%s,NODE T-21", id % 4, id / 4));
			
			// Take in lines until we hit the next node or EOF, discard last line
			while(++i < input.length) {
				if(input[i].startsWith("@")) break;
				node.add(input[i]);
			}
			
			nodeList.add(node);
			if(i >= input.length) break; 
		}
		
		return nodeList;
	}
	
	/**
	 * Separates the raw programs of each node from a node-map file
	 * 
	 * @param input The input file
	 * @return A list of node program lists
	 */
	private static ArrayList<ArrayList<String>> separateNMFormat(String[] input) {
		ArrayList<ArrayList<String>> nodeList = new ArrayList<>();
		
		// Loop over lines
		for(int i = 0;;) {
			// Node list
			ArrayList<String> node = new ArrayList<>();
			
			// Find next node
			while(!input[i].startsWith("<<")) i++;
			
			// Get type and location
			String s = input[i];
			int x = Integer.parseInt(s.substring(s.indexOf('(') + 1, s.indexOf(','))),
				y = Integer.parseInt(s.substring(s.indexOf(',') + 1, s.indexOf(')')));
			node.add(String.format("%s,%s,%s", x, y, s.substring(2, s.indexOf('('))));
			
			// Take lines until next node or EOF
			while(++i < input.length) {
				if(input[i].startsWith("<<")) break;
				node.add(input[i]);
			}
			
			nodeList.add(node);
			if(i >= input.length) break; 
		}
		
		return nodeList;
	}
	
	/**
	 * Reads the input file
	 * 
	 * @param path The path of the input file
	 * @return The contents of the input file
	 * @throws IOException
	 */
	private static String[] readInputFile(String path) throws IOException {
		//Open input file
		BufferedReader inputReader = new BufferedReader(new FileReader(path));
		ArrayList<String> lines = new ArrayList<>();
		String nextLine = "";
		
		while((nextLine = inputReader.readLine()) != null) lines.add(nextLine);
		
		inputReader.close();
		return lines.toArray(new String[lines.size()]);
	}
	
	/**
	 * Print the usage text
	 * 
	 * @param error The error to print beforehand
	 */
	private static void printUsage(String error) {
		System.err.println("\n" + error);
		System.err.println("Usage:\n" +
						   "TIS11Assembler [-v] input [output]\n" +
						   "\tinput:  The file to take input from\n" +
						   "\toutput: The file to output to\n" +
						   "\t-v:     Enable verbose console output\n");
	}
	
	/**
	 * An exception denoting an error occured while parsing outside of main
	 * Used to exit the program if there's an error during a parse method
	 * 
	 * @author Mechafinch
	 */
	@SuppressWarnings("serial")
	private static class ParseException extends RuntimeException {
		public ParseException(String msg, String coords, int line, Object ... others) {
			// Two calls to String.format because we can't do anything before the super() line
			super(String.format("Error at %s, line %d: ", coords, line) + String.format(msg, others));
		}
	}
}
