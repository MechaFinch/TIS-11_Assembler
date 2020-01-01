package mechafinch.tis11;

import java.io.BufferedReader;
import java.io.FileReader;
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
	 * TIS11Assembler [outputfile] inputfile
	 * 	outputfile: The file to output to
	 * 	inputfile: The file to take input from
	 */
	public static void main(String[] args) {
		
		// Sanitize arguments
		switch(args.length) {
			case 1:
				args = new String[] {args[0].substring(0, args[0].indexOf('.')) + "_bin.txt", args[0]};
				
			case 2:
				break;
			
			case 0:
				printUsage("Missing arguments");
				return;
				
			default:
				printUsage("Too many arguments");
				return;
		}
		
		String inputFilePath = args[1],
			   outputFilePath = args[0];
		
		// Echo arguments
		System.out.println("Input File: " + inputFilePath + "\nOutput File: " + outputFilePath + "\n");
		String[] inputLines;
		
		// Read the file
		try {
			inputLines = readInputFile(args[1]);
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
		nodes.forEach(n -> n.forEach(System.out::println));
		
		// Assemble into binary
		ArrayList<ArrayList<String>> binaries = new ArrayList<>();
		
		//Some common binary strings
		String immZero = "000000000000";
		
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
						System.err.println(String.format("Error at %s, line %d: Duplicate label '%s'", nodeCoords, i - 1, l));
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
			
			// We have pure opcodes now. Next we pass over and parse things
			for(int i = 1; i < node.size(); i++) {
				String instruction = node.get(i);
				
				// Determine opcode, convert to binary in each
				if(instruction.startsWith("NOP")) {
					bin.add("0000" + immZero + immZero);
				} else if(instruction.startsWith("MOV")) {
					// TODO
				} else if(instruction.startsWith("SWP")) {
					bin.add("0010" + immZero + immZero);
				} else if(instruction.startsWith("SAV")) {
					bin.add("0011" + immZero + immZero);
				} else if(instruction.startsWith("ADD")) {
					// TODO
				} else if(instruction.startsWith("SUB")) {
					// TODO
				} else if(instruction.startsWith("NEG")) {
					bin.add("0110" + immZero + immZero);
				} else if(instruction.startsWith("JMP")) {
					// TODO
				} else if(instruction.startsWith("JEZ")) {
					// TODO
				} else if(instruction.startsWith("JNZ")) {
					// TODO
				} else if(instruction.startsWith("JGZ")) {
					// TODO
				} else if(instruction.startsWith("JLZ")) {
					// TODO
				} else if(instruction.startsWith("JRO")) {
					// TODO
				} else if(instruction.startsWith("HCF")) {
					bin.add("1111" + immZero + immZero);
				} else { // Invalid opcode
					System.err.println(String.format("Error at %s, line %d: Invalid opcode '%s'", nodeCoords, i - 1, instruction));
					return;
				}
			}
			
			binaries.add(bin);
		}
		
		System.out.println("\n");
		binaries.forEach(n -> n.forEach(System.out::println));
		
		System.out.println();
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
						   "TIS11Assembler [-o output] input\n" +
						   "\toutput: The file to output to\n" +
						   "\tinput:  The file to take input from\n");
	}
}
