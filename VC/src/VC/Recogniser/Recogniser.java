/***
 * *
 * * Recogniser.java            
 * *
 ***/

/* At this stage, this parser accepts a subset of VC defined	by
 * the following grammar. 
 *
 * You need to modify the supplied parsing methods (if necessary) and 
 * add the missing ones to obtain a parser for the VC language.
 *
 * (23-*-March-*-2014)

program       -> func-decl

// declaration

func-decl     -> void identifier "(" ")" compound-stmt

identifier    -> ID

// statements 
compound-stmt -> "{" stmt* "}" 
stmt          -> continue-stmt
    	      |  expr-stmt
continue-stmt -> continue ";"
expr-stmt     -> expr? ";"

// expressions 
expr                -> assignment-expr
assignment-expr     -> additive-expr
additive-expr       -> multiplicative-expr
                    |  additive-expr "+" multiplicative-expr
multiplicative-expr -> unary-expr
	            |  multiplicative-expr "*" unary-expr
unary-expr          -> "-" unary-expr
		    |  primary-expr

primary-expr        -> identifier
 		    |  INTLITERAL
		    | "(" expr ")"
 */

package VC.Recogniser;
import java.util.Arrays;
import java.util.HashSet;
import VC.Scanner.Scanner;
import VC.Scanner.SourcePosition;
import VC.Scanner.Token;
import VC.ErrorReporter;

public class Recogniser {
	static {
		exprFirstSet = new HashSet<Integer>(Arrays.asList(Token.LPAREN, Token.PLUS, Token.MINUS, Token.NOT, Token.ID, Token.INTLITERAL, 
				Token.FLOATLITERAL, Token.BOOLEANLITERAL, Token.STRINGLITERAL));
		typeFirstSet = new HashSet<Integer>(Arrays.asList(Token.VOID, Token.BOOLEAN, Token.INT, Token.FLOAT));
	}

	private Scanner scanner;
	private ErrorReporter errorReporter;
	private Token currentToken;
	private static HashSet<Integer> exprFirstSet;
	private static HashSet<Integer> typeFirstSet;

	public Recogniser (Scanner lexer, ErrorReporter reporter) {
		scanner = lexer;
		errorReporter = reporter;

		currentToken = scanner.getToken();
	}

	// match checks to see f the current token matches tokenExpected.
	// If so, fetches the next token.
	// If not, reports a syntactic error.
	void match(int tokenExpected) throws SyntaxError {
		if (currentToken.kind == tokenExpected) {
			currentToken = scanner.getToken();
		} else {
			syntacticError("\"%\" expected here", Token.spell(tokenExpected));
		}
	}

	// accepts the current token and fetches the next
	void accept() {
		currentToken = scanner.getToken();
	}

	void syntacticError(String messageTemplate, String tokenQuoted) throws SyntaxError {
		SourcePosition pos = currentToken.position;
		errorReporter.reportError(messageTemplate, tokenQuoted, pos);
		throw(new SyntaxError());
	}


	// ========================== PROGRAMS ========================
	public void parseProgram() {
		try {
			while(currentToken.kind != Token.EOF) {

			}
			parseFuncDecl();
			if (currentToken.kind != Token.EOF) {
				syntacticError("\"%\" wrong result type for a function", currentToken.spelling);
			}
		}
		catch (SyntaxError s) {}
	}

	// ========================== DECLARATIONS ========================
	private void parseFuncDecl() throws SyntaxError {
		parseType();
		parseIdent();
		match(Token.LPAREN);
		match(Token.RPAREN);
		parseCompoundStmt();
	}

	private void parseVarDecl() throws SyntaxError {
		parseType();
		parseInitDeclaratorList();
		match(Token.SEMICOLON);
	}

	private void parseInitDeclaratorList() throws SyntaxError {
		parseInitDeclarator();
		while(currentToken.kind == Token.COMMA) {
			accept();
			parseInitDeclarator();
		}
	}

	private void parseInitDeclarator() throws SyntaxError {
		parseDeclarator();
		if(currentToken.kind == Token.EQ) {
			accept();
			parseInitialiser();
		}
	}

	private void parseDeclarator() throws SyntaxError {
		parseIdent();
		if(currentToken.kind == Token.LBRACKET) {
			accept();
			if(currentToken.kind == Token.INTLITERAL) {
				parseIntLiteral();
			}
			match(Token.RBRACKET);
		}
	}

	private void parseInitialiser() throws SyntaxError {
		if(currentToken.kind == Token.LCURLY) {
			accept();
			parseExpr();
			while(currentToken.kind == Token.COMMA) {
				accept();
				parseExpr();
			}
			match(Token.RCURLY);
		} else {
			parseExpr();
		}
	}

	private void parseType() throws SyntaxError {
		if(typeFirstSet.contains(currentToken.kind)) {
			accept();
		} else {
			syntacticError("Type expected here", "");
		}
	}

	// ======================= STATEMENTS ==============================
	private void parseCompoundStmt() throws SyntaxError {
		match(Token.LCURLY);
		parseStmtList();
		match(Token.RCURLY);
	}

	// Here, a new nontermial has been introduced to define { stmt } *
	private void parseStmtList() throws SyntaxError {
		while (currentToken.kind != Token.RCURLY) 
			parseStmt();
	}

	private void parseStmt() throws SyntaxError {
		switch (currentToken.kind) {
		case Token.LCURLY:
			parseCompoundStmt();
			break;
		case  Token.IF:
			parseIfStmt();
			break;
		case Token.FOR:
			parseForStmt();
			break;
		case Token.WHILE:
			parseWhileStmt();
			break;
		case Token.BREAK:
			parseBreakStmt();
			break;
		case Token.CONTINUE:
			parseContinueStmt();
			break;
		case Token.RETURN:
			parseReturnStmt();
			break;
		default:
			parseExprStmt();
			break;
		}
	}

	private void parseIfStmt() throws SyntaxError {
		match(Token.IF);
		match(Token.LPAREN);
		parseExpr();
		match(Token.RPAREN);
		parseStmt();
		if(currentToken.kind == Token.ELSE) {
			match(Token.ELSE);
			parseStmt();
		}
	}

	private void parseForStmt() throws SyntaxError {
		match(Token.FOR);
		match(Token.LPAREN);
		if(exprFirstSet.contains(currentToken.kind)) {
			parseExpr();
		}
		match(Token.SEMICOLON);
		if(exprFirstSet.contains(currentToken.kind)) {
			parseExpr();
		}
		match(Token.SEMICOLON);
		if(exprFirstSet.contains(currentToken.kind)) {
			parseExpr();
		}
		match(Token.RPAREN);
		parseStmt();

	}

	private void parseWhileStmt() throws SyntaxError {
		match(Token.WHILE);
		match(Token.RPAREN);
		parseExpr();
		match(Token.LPAREN);
		parseStmt();
	}

	private void parseBreakStmt() throws SyntaxError {
		match(Token.BREAK);
		match(Token.SEMICOLON);
	}

	private void parseContinueStmt() throws SyntaxError {
		match(Token.CONTINUE);
		match(Token.SEMICOLON);
	}

	private void parseReturnStmt() throws SyntaxError {
		match(Token.RETURN);
		if(exprFirstSet.contains(currentToken.kind)) {
			parseExpr();
		}
		match(Token.SEMICOLON);
	}

	private void parseExprStmt() throws SyntaxError {
		if(exprFirstSet.contains(currentToken.kind)) {
			parseExpr();
		}
		match(Token.SEMICOLON);
	}


	// ======================= IDENTIFIERS ======================
	// Call parseIdent rather than match(Token.ID). 
	// In Assignment 3, an Identifier node will be constructed in here.
	private void parseIdent() throws SyntaxError {
		if (currentToken.kind == Token.ID) {
			currentToken = scanner.getToken();
		} else 
			syntacticError("identifier expected here", "");
	}

	// ======================= OPERATORS ======================

	// Call acceptOperator rather than accept(). 
	// In Assignment 3, an Operator Node will be constructed in here.
	private void acceptOperator() throws SyntaxError {
		currentToken = scanner.getToken();
	}

	// ======================= EXPRESSIONS ======================
	private void parseExpr() throws SyntaxError {
		parseAssignExpr();
	}

	private void parseAssignExpr() throws SyntaxError {
		parseAdditiveExpr();
	}

	private void parseAdditiveExpr() throws SyntaxError {
		parseMultiplicativeExpr();
		while (currentToken.kind == Token.PLUS) {
			acceptOperator();
			parseMultiplicativeExpr();
		}
	}

	private void parseMultiplicativeExpr() throws SyntaxError {
		parseUnaryExpr();
		while (currentToken.kind == Token.MULT) {
			acceptOperator();
			parseUnaryExpr();
		}
	}

	private void parseUnaryExpr() throws SyntaxError {
		if(currentToken.kind == Token.PLUS || currentToken.kind == Token.MINUS || 
				currentToken.kind == Token.NOT) {
			acceptOperator();
			parseUnaryExpr();
		} else {
			parsePrimaryExpr();
		}
	}

	// here is not finished
	private void parsePrimaryExpr() throws SyntaxError {
		switch (currentToken.kind) {
		case Token.ID:
			parseIdent();
			break;
		case Token.LPAREN:
			accept();
			parseExpr();
			match(Token.RPAREN);
			break;
		case Token.INTLITERAL:
			parseIntLiteral();
			break;
		case Token.FLOATLITERAL:
			parseFloatLiteral();
			break;
		case Token.STRINGLITERAL:
			accept();
			break;
		default:
			syntacticError("illegal parimary expression", currentToken.spelling);
		}
	}

	// ========================== LITERALS ========================
	// Call these methods rather than accept().  In Assignment 3, 
	// literal AST nodes will be constructed inside these methods. 
	private void parseIntLiteral() throws SyntaxError {
		if (currentToken.kind == Token.INTLITERAL) {
			currentToken = scanner.getToken();
		} else 
			syntacticError("integer literal expected here", "");
	}

	private void parseFloatLiteral() throws SyntaxError {
		if (currentToken.kind == Token.FLOATLITERAL) {
			currentToken = scanner.getToken();
		} else 
			syntacticError("float literal expected here", "");
	}

	private void parseBooleanLiteral() throws SyntaxError {
		if (currentToken.kind == Token.BOOLEANLITERAL) {
			currentToken = scanner.getToken();
		} else 
			syntacticError("boolean literal expected here", "");
	}
}
