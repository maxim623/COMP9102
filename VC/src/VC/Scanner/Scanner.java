/**
 **	Scanner.java                        
 **/

package VC.Scanner;
import VC.ErrorReporter;

public final class Scanner { 

	private SourceFile sourceFile;
	private boolean debug;
	private ErrorReporter errorReporter;
	private StringBuffer currentSpelling;
	private char currentChar;
	private SourcePosition sourcePos;

	// =========================================================
	public Scanner(SourceFile source, ErrorReporter reporter) {
		sourceFile = source;
		errorReporter = reporter;
		currentChar = sourceFile.getNextChar();
		debug = false;
		sourcePos = new SourcePosition(1, 1, 0);
		// you may initialise your counters for line and column numbers here
	}

	public void enableDebugging() {
		debug = true;
	}

	// accept gets the next character from the source program.
	private void accept() {
		currentSpelling.append(currentChar);
		++sourcePos.charFinish;
		currentChar = sourceFile.getNextChar();
		// you may save the lexeme of the current token incrementally here
		// you may also increment your line and column counters here
	}

	// inspectChar returns the n-th character after currentChar
	// in the input stream. 
	//
	// If there are fewer than nthChar characters between currentChar 
	// and the end of file marker, SourceFile.eof is returned.
	// 
	// Both currentChar and the current position in the input stream
	// are *not* changed. Therefore, a subsequent call to accept()
	// will always return the next char after currentChar.

	private char inspectChar(int nthChar) {
		return sourceFile.inspectChar(nthChar);
	}

	private int nextToken() {
		// Tokens: separators, operators, literals, identifiers and keywords
		switch (currentChar) {
		// recognize separators and operators
		case '(':
			accept();
			return Token.LPAREN;
		case ')':
			accept();
			return Token.RPAREN;
		case '{':
			accept();
			return Token.LCURLY;
		case '}':
			accept();
			return Token.RCURLY;
		case '[':
			accept();
			return Token.LBRACKET;
		case ']':
			accept();
			return Token.RBRACKET;
		case ';':
			accept();
			return Token.SEMICOLON;
		case ',':
			accept();
			return Token.COMMA;
		case '+':
			accept();
			return Token.PLUS;
		case '-':
			accept();
			return Token.MINUS;
		case '*':
			accept();
			return Token.MULT;
		case '/':
			accept();
			return Token.DIV;
		case '<':
			accept();
			if(currentChar == '=') {
				accept();
				return Token.LTEQ;
			} else {
				return Token.LT;
			}
		case '>':
			accept();
			if(currentChar == '=') {
				accept();
				return Token.GTEQ;
			} else {
				return Token.GT;
			}
		case '=':
			accept();
			if(currentChar == '=') {
				accept();
				return Token.EQEQ;
			} else {
				return Token.EQ;
			}
		case '!':
			accept();
			if(currentChar == '=') {
				accept();
				return Token.NOTEQ;
			} else {
				return Token.NOT;
			}
		case '|':
			accept();
			if (currentChar == '|') {
				accept();
				return Token.OROR;
			} else {
				return Token.ERROR;
			}
		case '&':
			accept();
			if(currentChar == '&') {
				accept();
				return Token.ANDAND;
			} else {
				return Token.ERROR;
			}
			// recognize numbers
		case '0':case '1':case '2':case '3':case '4':
		case '5':case '6':case '7':case '8':case '9':
			accept();
			while(Character.isDigit(currentChar)) {
				accept();
			}
			// up to here, integer number is recognized
			switch(currentChar) {
			case '.':
				accept();
				// up to here, float number like 123. is recognized
				switch(currentChar) {
				case '0':case '1':case '2':case '3':case '4':
				case '5':case '6':case '7':case '8':case '9':
					accept();
					while(Character.isDigit(currentChar)) {
						accept();
						// up to here, float number like 12.34 is recognized
					}
					switch(currentChar) {
					case 'E':
					case 'e':
						return recognizeExp(Token.FLOATLITERAL);
					default:
						// float number like 12.34 is accepted
						return Token.FLOATLITERAL;
					}
				case 'E':
				case 'e':
					return recognizeExp(Token.FLOATLITERAL);
				default:
					// float number like 12. is accepted 
					return Token.FLOATLITERAL;
				}
			case 'E':
			case 'e':
				return recognizeExp(Token.INTLITERAL);
			default:
				// accept integer number
				return Token.INTLITERAL;
			}
			// in this case, scanner see . for the first time
		case '.':
			accept();
			switch(currentChar) {
			case '0':case '1':case '2':case '3':case '4':
			case '5':case '6':case '7':case '8':case '9':
				accept();
				while(Character.isDigit(currentChar)) {
					accept();
				}
				switch(currentChar) {
				case 'E':
				case 'e':
					return recognizeExp(Token.FLOATLITERAL);
				default:
					return Token.FLOATLITERAL;

				}
			default:
				return Token.ERROR;
			}
		case '"':
			// here recognize string
			currentChar = sourceFile.getNextChar();
			sourcePos.charFinish++;
			while(true) {
				switch(currentChar) {
				case '\\':
					// here recognize escape character
					switch(inspectChar(1)) {
					case 'b':
						// accept back slash
						// accept escape character
						currentChar = sourceFile.getNextChar();
						currentChar = sourceFile.getNextChar();
						currentSpelling.append('\b');
						sourcePos.charFinish += 2;
						break;
					case 'f':
						currentChar = sourceFile.getNextChar();
						currentChar = sourceFile.getNextChar();
						currentSpelling.append('\f');
						sourcePos.charFinish += 2;
						break;
					case 'n':
						currentChar = sourceFile.getNextChar();
						currentChar = sourceFile.getNextChar();
						currentSpelling.append('\n');
						sourcePos.charFinish += 2;
						break;
					case 'r':
						currentChar = sourceFile.getNextChar();
						currentChar = sourceFile.getNextChar();
						currentSpelling.append('\r');
						sourcePos.charFinish += 2;
						break;
					case 't':
						currentChar = sourceFile.getNextChar();
						currentChar = sourceFile.getNextChar();
						currentSpelling.append('\t');
						sourcePos.charFinish +=2;
						break;
					case '\'':
						currentChar = sourceFile.getNextChar();
						currentChar = sourceFile.getNextChar();
						currentSpelling.append('\'');
						sourcePos.charFinish += 2;
						break;
					case '\"':
						currentChar = sourceFile.getNextChar();
						currentChar = sourceFile.getNextChar();
						currentSpelling.append('\"');
						sourcePos.charFinish += 2;
						break;
					case '\\':
						currentChar = sourceFile.getNextChar();
						currentChar = sourceFile.getNextChar();
						currentSpelling.append('\\');
						sourcePos.charFinish += 2;
						break;
					default:
						// remember beginning position of illegal escape character
						// drop slash and illegal escape character
						// report error message
						currentChar = sourceFile.getNextChar();
						sourcePos.charFinish++;
						int illegalEscpCharPos = sourcePos.charFinish;
						String errorMsg = "\\" + currentChar + ": illegal escape character.";
						currentChar = sourceFile.getNextChar();
						sourcePos.charFinish++;
						errorReporter.reportError(errorMsg, null, new SourcePosition(sourcePos.lineStart, illegalEscpCharPos, sourcePos.charFinish));
					}
					break;
				case '\n':
				case SourceFile.eof:
					errorReporter.reportError(currentSpelling + ": unterminated string.", null, sourcePos);
					return Token.ERROR;
				case '"':
					// the end of string
					currentChar = sourceFile.getNextChar();
					sourcePos.charFinish++;
					return Token.STRINGLITERAL;
				default:
					accept();
				}
			}
		case SourceFile.eof:
			currentSpelling.append(Token.spell(Token.EOF));
			sourcePos.charFinish++;
			return Token.EOF;
		default:
			if(Character.isLetter(currentChar) || currentChar == '_') {
				accept();
				while(Character.isLetterOrDigit(currentChar) || currentChar == '_') {
					accept();
				}
				return distinguishID();
			}
			break;
		}
		accept();
		return Token.ERROR;
	}

	private int distinguishID() {
		for(int i = 0; i <= 10; i++) {
			if(Token.spell(i).equals(currentSpelling)) {
				// accept keyword
				return i;
			}
		}
		if("true".contentEquals(currentSpelling) || "false".contentEquals(currentSpelling)) {
			return Token.BOOLEANLITERAL;
		} else {
			// accept common identifier 
			return Token.ID;
		}
	}

	private int recognizeExp(int currentState) {
		// up to here, current char is E or e and it is not accepted
		switch(inspectChar(1)) {
		case '0':case '1':case '2':case '3':case '4':
		case '5':case '6':case '7':case '8':case '9':
			// recognize E or e
			accept();
			while(Character.isDigit(currentChar)) {
				accept();
			}
			return Token.FLOATLITERAL;
		case '+':
		case '-':
			if(Character.isDigit(inspectChar(2))) {
				// recognize E or e 
				accept();
				// recognize + or 1
				accept();
				while(Character.isDigit(currentChar)) {
					accept();
				}
				// here float number like **e+2 is recognized
				return Token.FLOATLITERAL;
			}
		default:
			return currentState;
		}
	}

	private void recognizeInComment() {
		switch(currentChar) {
		case '\t':
			currentChar = sourceFile.getNextChar();
			// why is that?
			sourcePos.charStart += (8 - (sourcePos.charStart - 1) % 8);
			break;
		case '\n':
			currentChar = sourceFile.getNextChar();
			sourcePos.charStart = 1;
			sourcePos.lineStart++;
			break;
		default:
			currentChar = sourceFile.getNextChar();
			sourcePos.charStart++;
			break;
		}
	}

	private void skipSpaceAndComments() {
		while(true) {
			switch(currentChar) {
			case '/':
				switch(inspectChar(1)) {
				case '/':
					// ignore first slash
					recognizeInComment();
					// ignore second slash
					recognizeInComment();
					while(currentChar != '\n' && currentChar != SourceFile.eof) {
						//recognize one-line comment
						recognizeInComment();
					}
					break;
				case '*':
					// for error message
					int comtStartLineNo = sourcePos.lineStart;
					int comtStartCharNo = sourcePos.charStart;
					// ignore slash
					recognizeInComment();
					// ignore star
					recognizeInComment();
					while(true) {
						if(currentChar == '*') {
							if(inspectChar(1) == '/') {
								// ignore star
								recognizeInComment();
								// ignore slash
								recognizeInComment();
								// break from loop
								break;
							} else {
								recognizeInComment();
							}
						} else if(currentChar == SourceFile.eof) {
							errorReporter.reportError("Unterminated comment.", null, new SourcePosition(comtStartLineNo, comtStartCharNo, comtStartCharNo));
							return;
						} else {
							recognizeInComment();
						}
					}
					break;
				default:
					return;
				}
				break;
			case ' ':
			case '\t':
			case  '\n':
				recognizeInComment();
				break;
			default:
				// not a comment
				return;
			}
		}
	}

	public Token getToken() {
		Token tok;
		int kind;
		sourcePos.charStart = sourcePos.charFinish + 1;
		// skip white space and comments
		skipSpaceAndComments();
		sourcePos.lineFinish = sourcePos.lineStart;
		sourcePos.charFinish = sourcePos.charStart - 1;
		currentSpelling = new StringBuffer("");
		// You must record the position of the current token somehow
		kind = nextToken();
		tok = new Token(kind, currentSpelling.toString(), sourcePos);
		// * do not remove these three lines
		if (debug) {
			System.out.println(tok);
		}
		return tok;
	}
}
