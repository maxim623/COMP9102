/*
 * Parser.java            
 *
 * This parser for a subset of the VC language is intended to 
 *  demonstrate how to create the AST nodes, including (among others): 
 *  [1] a list (of statements)
 *  [2] a function
 *  [3] a statement (which is an expression statement), 
 *  [4] a unary expression
 *  [5] a binary expression
 *  [6] terminals (identifiers, integer literals and operators)
 *
 * In addition, it also demonstrates how to use the two methods start 
 * and finish to determine the position information for the start and 
 * end of a construct (known as a phrase) corresponding an AST node.
 *
 * NOTE THAT THE POSITION INFORMATION WILL NOT BE MARKED. HOWEVER, IT CAN BE
 * USEFUL TO DEBUG YOUR IMPLEMENTATION.
 *
 * (10-*-April-*-2015)


program       -> func-decl
func-decl     -> type identifier "(" ")" compound-stmt
type          -> void
identifier    -> ID
// statements
compound-stmt -> "{" stmt* "}" 
stmt          -> expr-stmt
expr-stmt     -> expr? ";"
// expressions 
expr                -> additive-expr
additive-expr       -> multiplicative-expr
                    |  additive-expr "+" multiplicative-expr
                    |  additive-expr "-" multiplicative-expr
multiplicative-expr -> unary-expr
	            |  multiplicative-expr "*" unary-expr
	            |  multiplicative-expr "/" unary-expr
unary-expr          -> "-" unary-expr
		    |  primary-expr

primary-expr        -> identifier
 		    |  INTLITERAL
		    | "(" expr ")"
 */

package VC.Parser;
import java.util.Arrays;
import java.util.HashSet;

import VC.Scanner.Scanner;
import VC.Scanner.SourcePosition;
import VC.Scanner.Token;
import VC.ErrorReporter;
import VC.ASTs.*;

final class Type_ID {
	Type typeAST;
	Ident idAST;
	public Type_ID(Type _typeAST, Ident _idAST) {
		typeAST = _typeAST;
		idAST = _idAST;
	}
}

public class Parser {
	static {
		exprFirstSet = new HashSet<Integer>(Arrays.asList(Token.LPAREN, Token.PLUS, Token.MINUS, Token.NOT, Token.ID, 
				Token.INTLITERAL, Token.FLOATLITERAL, Token.BOOLEANLITERAL, Token.STRINGLITERAL));
		typeFirstSet = new HashSet<Integer>(Arrays.asList(Token.VOID, Token.BOOLEAN, Token.INT, Token.FLOAT));
	}
	private Scanner scanner;
	private ErrorReporter errorReporter;
	private Token currentToken;
	private SourcePosition previousTokenPosition;
	private SourcePosition dummyPos = new SourcePosition();
	private static HashSet<Integer> exprFirstSet;
	private static HashSet<Integer> typeFirstSet;

	public Parser (Scanner lexer, ErrorReporter reporter) {
		scanner = lexer;
		errorReporter = reporter;

		previousTokenPosition = new SourcePosition();

		currentToken = scanner.getToken();
	}

	// match checks to see f the current token matches tokenExpected.
	// If so, fetches the next token.
	// If not, reports a syntactic error.

	void match(int tokenExpected) throws SyntaxError {
		if (currentToken.kind == tokenExpected) {
			previousTokenPosition = currentToken.position;
			currentToken = scanner.getToken();
		} else {
			syntacticError("\"%\" expected here", Token.spell(tokenExpected));
		}
	}

	void accept() {
		previousTokenPosition = currentToken.position;
		currentToken = scanner.getToken();
	}

	void syntacticError(String messageTemplate, String tokenQuoted) throws SyntaxError {
		SourcePosition pos = currentToken.position;
		errorReporter.reportError(messageTemplate, tokenQuoted, pos);
		throw(new SyntaxError());
	}

	// start records the position of the start of a phrase.
	// This is defined to be the position of the first
	// character of the first token of the phrase.

	void start(SourcePosition position) {
		position.lineStart = currentToken.position.lineStart;
		position.charStart = currentToken.position.charStart;
	}

	// finish records the position of the end of a phrase.
	// This is defined to be the position of the last
	// character of the last token of the phrase.

	void finish(SourcePosition position) {
		position.lineFinish = previousTokenPosition.lineFinish;
		position.charFinish = previousTokenPosition.charFinish;
	}

	void copyStart(SourcePosition from, SourcePosition to) {
		to.lineStart = from.lineStart;
		to.charStart = from.charStart;
	}

	// ========================== PROGRAMS ========================

	public Program parseProgram() {

		Program programAST = null;

		SourcePosition programPos = new SourcePosition();
		start(programPos);

		try {
			List dlAST = parseFuncDeclList();
			finish(programPos);
			programAST = new Program(dlAST, programPos); 
			if (currentToken.kind != Token.EOF) {
				syntacticError("\"%\" unknown type", currentToken.spelling);
			}
		}
		catch (SyntaxError s) { return null; }
		return programAST;
	}

	// ========================== DECLARATIONS ========================

	List parseFuncDeclList() throws SyntaxError {
		List dlAST = null;
		Decl dAST = null;

		SourcePosition funcPos = new SourcePosition();
		start(funcPos);

		dAST = parseFuncDecl();

		if (currentToken.kind == Token.VOID) {
			dlAST = parseFuncDeclList();
			finish(funcPos);
			dlAST = new DeclList(dAST, dlAST, funcPos);
		} else if (dAST != null) {
			finish(funcPos);
			dlAST = new DeclList(dAST, new EmptyDeclList(dummyPos), funcPos);
		}
		if (dlAST == null) 
			dlAST = new EmptyDeclList(dummyPos);

		return dlAST;
	}

	Decl parseFuncDecl() throws SyntaxError {

		Decl fAST = null; 

		SourcePosition funcPos = new SourcePosition();
		start(funcPos);

		Type tAST = parseType();
		Ident iAST = parseIdent();
		List fplAST = parseParaList();
		Stmt cAST = parseCompoundStmt();
		finish(funcPos);
		fAST = new FuncDecl(tAST, iAST, fplAST, cAST, funcPos);
		return fAST;
	}

	Type_ID parseDeclarator(Type type) throws SyntaxError {
		SourcePosition declaratorPos = new SourcePosition();
		start(declaratorPos);
		Ident idAST = parseIdent();
		if(currentToken.kind == Token.LBRACKET) {
			accept();
			Expr indexExpr = null;
			if(currentToken.kind == Token.INTLITERAL) {
				SourcePosition indexPos = new SourcePosition();
				start(indexPos);
				IntLiteral intLiteral = parseIntLiteral();
				finish(indexPos);
				indexExpr = new IntExpr(intLiteral, indexPos);
				match(Token.RBRACKET);
			} else {
				indexExpr = new EmptyExpr(dummyPos);
				match(Token.RBRACKET);
			}
			finish(declaratorPos);
			ArrayType arrayTypeAST = new ArrayType(type, indexExpr, declaratorPos);
			return new Type_ID(arrayTypeAST, idAST);
		} else {
			finish(declaratorPos);
			return new Type_ID(type, idAST);
		}
	}

	//  ======================== TYPES ==========================

	Type parseType() throws SyntaxError {
		Type typeAST = null;
		SourcePosition typePos = new SourcePosition();
		start(typePos);
		switch(currentToken.kind) {
		case Token.VOID:
			typeAST = new VoidType(typePos);
			break;
		case Token.BOOLEAN:
			typeAST = new BooleanType(typePos);
			break;
		case Token.INT:
			typeAST = new IntType(typePos);
			break;
		case Token.FLOAT:
			typeAST = new FloatType(typePos);
			break;
		default:
			syntacticError("type expected here", "");
			break;
		}
		return typeAST;
	}

	// ======================= STATEMENTS ==============================

	Stmt parseCompoundStmt() throws SyntaxError {
		Stmt cAST = null; 
		SourcePosition stmtPos = new SourcePosition();
		start(stmtPos);
		match(Token.LCURLY);
		// Insert code here to build a DeclList node for variable declarations
		List slAST = parseStmtList();
		match(Token.RCURLY);
		finish(stmtPos);
		/* In the subset of the VC grammar, no variable declarations are
		 * allowed. Therefore, a block is empty iff it has no statements.
		 */
		if (slAST instanceof EmptyStmtList) {
			cAST = new EmptyCompStmt(stmtPos);
		}
		else {
			cAST = new CompoundStmt(new EmptyDeclList(dummyPos), slAST, stmtPos);
		}
		return cAST;
	}

	// Here, a new nontermial has been introduced to define { stmt } *
	List parseStmtList() throws SyntaxError {
		List slAST = null; 
		SourcePosition stmtPos = new SourcePosition();
		start(stmtPos);
		if (currentToken.kind != Token.RCURLY) {
			Stmt sAST = parseStmt();
			if (currentToken.kind != Token.RCURLY) {
				slAST = parseStmtList();
				finish(stmtPos);
				slAST = new StmtList(sAST, slAST, stmtPos);
			} else {
				finish(stmtPos);
				slAST = new StmtList(sAST, new EmptyStmtList(dummyPos), stmtPos);
			}
		}
		else
			slAST = new EmptyStmtList(dummyPos);
		return slAST;
	}

	Stmt parseStmt() throws SyntaxError {
		Stmt sAST = null;
		switch(currentToken.kind) {
		case Token.LCURLY:
			sAST = parseCompoundStmt();
			break;
		case Token.IF:
			sAST = parseIfStmt();
			break;
		case Token.FOR:
			sAST = parseForStmt();
			break;
		case Token.WHILE:
			sAST = parseWhileStmt();
			break;
		case Token.BREAK:
			sAST = parseBreakStmt();
			break;
		case Token.CONTINUE:
			sAST = parseContinueStmt();
			break;
		case Token.RETURN:
			sAST = parseReturnStmt();
			break;
		default:
			sAST = parseExprStmt();
			break;
		}
		return sAST;
	}

	Stmt parseIfStmt() throws SyntaxError {
		SourcePosition ifPos =  new SourcePosition();
		start(ifPos);
		Stmt ifAST = null;
		Expr condAST = null;
		Stmt thenAST = null, elseAST = null;
		accept();
		match(Token.LPAREN);
		condAST = parseExpr();
		match(Token.RPAREN);
		thenAST = parseStmt();
		finish(ifPos);
		if(currentToken.kind == Token.ELSE) {
			accept();
			elseAST = parseStmt();
			finish(ifPos);
			ifAST = new IfStmt(condAST, thenAST, elseAST, ifPos);
		} else {
			ifAST = new IfStmt(condAST, thenAST, ifPos);
		}
		return ifAST;
	}

	Stmt parseForStmt() throws SyntaxError {
		SourcePosition forPos = new SourcePosition();
		start(forPos);
		Expr _1ExprAST =  null, _2ExprAST = null, _3ExprAST = null;
		Stmt bodyAST = null;
		accept();
		match(Token.LPAREN);
		if(exprFirstSet.contains(currentToken.kind)) {
			_1ExprAST = parseExpr();
		} else {
			_1ExprAST = new EmptyExpr(dummyPos);
		}
		match(Token.SEMICOLON);
		if(exprFirstSet.contains(currentToken.kind)) {
			_2ExprAST = parseExpr();
		} else {
			_2ExprAST = new EmptyExpr(dummyPos);
		}
		match(Token.SEMICOLON);
		if(exprFirstSet.contains(currentToken.kind)) {
			_3ExprAST = parseExpr();
		} else {
			_3ExprAST = new EmptyExpr(dummyPos);
		}
		match(Token.RPAREN);
		bodyAST = parseStmt();
		finish(forPos);
		return new ForStmt(_1ExprAST, _2ExprAST, _3ExprAST, bodyAST, forPos);
	}

	Stmt parseWhileStmt() throws SyntaxError {
		SourcePosition whilePos = new SourcePosition();
		start(whilePos);
		Expr condAST = null;
		Stmt bodyAST = null;
		accept();
		match(Token.LPAREN);
		condAST = parseExpr();
		match(Token.RPAREN);
		bodyAST = parseStmt();
		finish(whilePos);
		return new WhileStmt(condAST, bodyAST, whilePos);
	}

	Stmt parseBreakStmt() throws SyntaxError {
		SourcePosition breakPos = new SourcePosition();
		start(breakPos);
		accept();
		match(Token.SEMICOLON);
		finish(breakPos);
		return new BreakStmt(breakPos);
	}

	Stmt parseContinueStmt() throws SyntaxError {
		SourcePosition contPos = new SourcePosition();
		start(contPos);
		accept();
		match(Token.SEMICOLON);
		finish(contPos);
		return new ContinueStmt(contPos);
	}

	Stmt parseReturnStmt() throws SyntaxError {
		SourcePosition retPos = new SourcePosition();
		start(retPos);
		Expr retExprAST = null;
		accept();
		if(exprFirstSet.contains(currentToken)) {
			retExprAST = parseExpr();
		} else {
			retExprAST = new EmptyExpr(dummyPos);
		}
		match(Token.SEMICOLON);
		finish(retPos);
		return new ReturnStmt(retExprAST, retPos);
	}

	Stmt parseExprStmt() throws SyntaxError {
		Stmt sAST = null;
		SourcePosition stmtPos = new SourcePosition();
		start(stmtPos);
		if (exprFirstSet.contains(currentToken)) {
			Expr eAST = parseExpr();
			match(Token.SEMICOLON);
			finish(stmtPos);
			sAST = new ExprStmt(eAST, stmtPos);
		} else {
			match(Token.SEMICOLON);
			finish(stmtPos);
			sAST = new ExprStmt(new EmptyExpr(dummyPos), stmtPos);
		}
		return sAST;
	}


	// ======================= PARAMETERS =======================

	// ======================= EXPRESSIONS ======================
	/*
	 * left recursion
	 * A -> B | A op B
	 * 
	 * A->BA'
	 * A'-> op B | epsilon
	 * 
	 * A->B(op B)*
	 * */
	Expr parseExpr() throws SyntaxError {
		return parseAssignExpr();
	}

	Expr parseAssignExpr() throws SyntaxError {
		SourcePosition assignPos = new SourcePosition();
		start(assignPos);
		Expr assignAST = parseCondOrExpr();
		if(currentToken.kind == Token.EQ) {
			accept();
			Expr subAssExpr = parseAssignExpr();
			finish(assignPos);
			assignAST = new AssignExpr(assignAST, subAssExpr, assignPos);
		}
		return assignAST;
	}

	Expr parseCondOrExpr() throws SyntaxError {
		SourcePosition condOrPos = new SourcePosition();
		start(condOrPos);
		Expr condOrAST = parseCondAndEpxr();
		while(currentToken.kind == Token.OROR) {
			Operator op = acceptOperator();
			Expr subExpr = parseCondAndEpxr();
			finish(condOrPos);
			condOrAST = new BinaryExpr(condOrAST, op, subExpr, condOrPos);
		}
		return condOrAST;
	}

	Expr parseCondAndEpxr() throws SyntaxError {
		SourcePosition conAndPos = new SourcePosition();
		start(conAndPos);
		Expr condAndAST = parseEqualityExpr();
		while(currentToken.kind == Token.ANDAND) {
			Operator op = acceptOperator();
			Expr subExpr = parseEqualityExpr();
			finish(conAndPos);
			condAndAST = new BinaryExpr(condAndAST, op, subExpr, conAndPos);
		}
		return condAndAST;
	}

	Expr parseEqualityExpr() throws SyntaxError {
		SourcePosition eqPos = new SourcePosition();
		start(eqPos);
		Expr eqAST = parseRelExpr();
		while(currentToken.kind == Token.EQEQ || currentToken.kind == Token.NOTEQ) {
			Operator op = acceptOperator();
			Expr subExpr = parseRelExpr();
			finish(eqPos);
			eqAST = new BinaryExpr(eqAST, op, subExpr, eqPos);
		}
		return eqAST;
	}

	Expr parseRelExpr() throws SyntaxError {
		SourcePosition relExprPos = new SourcePosition();
		start(relExprPos);
		Expr exprAST = parseAdditiveExpr();
		while(currentToken.kind == Token.GT || currentToken.kind == Token.GTEQ ||
				currentToken.kind == Token.LT || currentToken.kind == Token.LTEQ) {
			Operator op = acceptOperator();
			Expr subExpr = parseAdditiveExpr();
			finish(relExprPos);
			exprAST = new BinaryExpr(exprAST, op, subExpr, relExprPos);
		}
		return exprAST;
	}

	Expr parseAdditiveExpr() throws SyntaxError {
		SourcePosition addStartPos = new SourcePosition();
		start(addStartPos);
		Expr exprAST = parseMultiplicativeExpr();
		while (currentToken.kind == Token.PLUS || currentToken.kind == Token.MINUS) {
			Operator opAST = acceptOperator();
			Expr subExpr = parseMultiplicativeExpr();
			SourcePosition addPos = new SourcePosition();
			copyStart(addStartPos, addPos);
			finish(addPos);
			exprAST = new BinaryExpr(exprAST, opAST, subExpr, addPos);
		}
		return exprAST;
	}

	Expr parseMultiplicativeExpr() throws SyntaxError {
		SourcePosition multStartPos = new SourcePosition();
		start(multStartPos);
		Expr exprAST = parseUnaryExpr();
		while (currentToken.kind == Token.MULT || currentToken.kind == Token.DIV) {
			Operator opAST = acceptOperator();
			Expr subExpr = parseUnaryExpr();
			SourcePosition multPos = new SourcePosition();
			copyStart(multStartPos, multPos);
			finish(multPos);
			exprAST = new BinaryExpr(exprAST, opAST, subExpr, multPos);
		}
		return exprAST;
	}

	Expr parseUnaryExpr() throws SyntaxError {
		SourcePosition unaryPos = new SourcePosition();
		start(unaryPos);
		Expr exprAST = null;
		Expr subExprAST = null;
		Operator opAST = null;
		switch (currentToken.kind) {
		case Token.PLUS:
		case Token.MINUS:
		case Token.NOT:
			opAST = acceptOperator();
			subExprAST = parseUnaryExpr();
			finish(unaryPos);
			exprAST = new UnaryExpr(opAST, subExprAST, unaryPos);
			break;
		default:
			exprAST = parsePrimaryExpr();
			break;
		}
		return exprAST;
	}

	Expr parsePrimaryExpr() throws SyntaxError {
		SourcePosition primPos = new SourcePosition();
		start(primPos);
		Expr exprAST = null;		
		start(primPos);
		switch (currentToken.kind) {
		case Token.ID:
			Ident iAST = parseIdent();
			finish(primPos);
			if(currentToken.kind == Token.LPAREN) {
				SourcePosition callPos = new SourcePosition();
				copyStart(primPos, callPos);
				accept();				
				List argListAST = parseArgList();
				match(Token.RPAREN);
				finish(callPos);
				exprAST = new CallExpr(iAST, argListAST, callPos);
			} else if (currentToken.kind == Token.LBRACKET){
				Var arrayVar = new SimpleVar(iAST, primPos);
				SourcePosition arrayPos = new SourcePosition();
				copyStart(primPos, arrayPos);
				accept();
				Expr indexAST = parseExpr();
				finish(arrayPos);
				exprAST = new ArrayExpr(arrayVar, indexAST, arrayPos);
			} else {
				Var var = new SimpleVar(iAST, primPos);
				exprAST = new VarExpr(var, primPos);
			}
			break;
		case Token.LPAREN:
			accept();
			exprAST = parseExpr();
			match(Token.RPAREN);
			break;
		case Token.INTLITERAL:
			IntLiteral ilAST = parseIntLiteral();
			finish(primPos);
			exprAST = new IntExpr(ilAST, primPos);
			break;
		case Token.FLOATLITERAL:
			FloatLiteral flAST = parseFloatLiteral();
			finish(primPos);
			exprAST = new FloatExpr(flAST, primPos);
			break;
		case Token.BOOLEANLITERAL:
			BooleanLiteral bAST = parseBooleanLiteral();
			finish(primPos);
			exprAST = new BooleanExpr(bAST, primPos);
			break;
		case Token.STRINGLITERAL:
			StringLiteral strAST = parseStringLiteral();
			finish(primPos);
			exprAST = new StringExpr(strAST, primPos);
			break;
		default:
			syntacticError("illegal primary expression", currentToken.spelling);
		}
		return exprAST;
	}

	List parseParaList() throws SyntaxError {
		SourcePosition paraListPos = new SourcePosition();
		start(paraListPos);
		match(Token.LPAREN);
		List paraListAST = null;
		if(currentToken.kind == Token.RPAREN) {
			finish(paraListPos);
			paraListAST = new EmptyDeclList(dummyPos);
		} else {
			paraListAST = parseProperParaList();
			match(Token.RPAREN);
			finish(paraListPos);
		}
		return paraListAST;
	}

	List parseProperParaList() throws SyntaxError {
		SourcePosition properParaListPos = new SourcePosition();
		start(properParaListPos);
		ParaDecl declAST = parseParaDecl();
		List listAST = null;
		if(currentToken.kind == Token.COMMA) {
			accept();
			List subList = parseProperParaList();
			finish(properParaListPos);
			listAST = new DeclList(declAST, subList, properParaListPos);
		} else {
			finish(properParaListPos);
			listAST = new DeclList(declAST, new EmptyDeclList(dummyPos), properParaListPos);
		}
		return listAST;
	}

	ParaDecl parseParaDecl() throws SyntaxError {
		SourcePosition paraDeclPos = new SourcePosition();
		start(paraDeclPos);
		Type type = parseType();
		Type_ID type_ID = parseDeclarator(type);
		finish(paraDeclPos);
		return new ParaDecl(type, type_ID.idAST, paraDeclPos);
	}

	List parseArgList() throws SyntaxError {
		SourcePosition argListPos = new SourcePosition();
		start(argListPos);
		match(Token.RPAREN);
		List properArgListAST = null;
		if(currentToken.kind == Token.RPAREN) {
			accept();
			finish(argListPos);
			properArgListAST = new EmptyArgList(dummyPos);
		} else {
			properArgListAST = parseProperArgList();
		}
		return properArgListAST;
	}

	List parseProperArgList() throws SyntaxError {
		SourcePosition argListPos = new SourcePosition();
		start(argListPos);
		Arg argAST = parseArg();
		if(currentToken.kind == Token.COMMA) {
			accept();
			List argListAST = parseProperArgList();
			finish(argListPos);
			return new ArgList(argAST, argListAST, argListPos);
		} else {
			match(Token.RPAREN);
			finish(argListPos);
			return new ArgList(argAST, new EmptyArgList(dummyPos), argListPos);
		}
	}

	Arg parseArg() throws SyntaxError {
		SourcePosition argPos = new SourcePosition();
		start(argPos);
		Expr exprAST = parseExpr();
		finish(argPos);
		return new Arg(exprAST, argPos);
	}

	// ========================== ID, OPERATOR and LITERALS ========================

	Ident parseIdent() throws SyntaxError {

		Ident I = null; 

		if (currentToken.kind == Token.ID) {
			previousTokenPosition = currentToken.position;
			String spelling = currentToken.spelling;
			I = new Ident(spelling, previousTokenPosition);
			currentToken = scanner.getToken();
		} else 
			syntacticError("identifier expected here", "");
		return I;
	}

	// acceptOperator parses an operator, and constructs a leaf AST for it
	Operator acceptOperator() throws SyntaxError {
		Operator O = null;
		previousTokenPosition = currentToken.position;
		String spelling = currentToken.spelling;
		O = new Operator(spelling, previousTokenPosition);
		currentToken = scanner.getToken();
		return O;
	}


	IntLiteral parseIntLiteral() throws SyntaxError {
		IntLiteral IL = null;
		if (currentToken.kind == Token.INTLITERAL) {
			String spelling = currentToken.spelling;
			accept();
			IL = new IntLiteral(spelling, previousTokenPosition);
		} else 
			syntacticError("integer literal expected here", "");
		return IL;
	}

	FloatLiteral parseFloatLiteral() throws SyntaxError {
		FloatLiteral FL = null;
		if (currentToken.kind == Token.FLOATLITERAL) {
			String spelling = currentToken.spelling;
			accept();
			FL = new FloatLiteral(spelling, previousTokenPosition);
		} else 
			syntacticError("float literal expected here", "");
		return FL;
	}

	BooleanLiteral parseBooleanLiteral() throws SyntaxError {
		BooleanLiteral BL = null;
		if (currentToken.kind == Token.BOOLEANLITERAL) {
			String spelling = currentToken.spelling;
			accept();
			BL = new BooleanLiteral(spelling, previousTokenPosition);
		} else 
			syntacticError("boolean literal expected here", "");
		return BL;
	}

	StringLiteral parseStringLiteral() throws SyntaxError {
		StringLiteral strL = null;
		if(currentToken.kind == Token.STRINGLITERAL) {
			String spelling = currentToken.spelling;
			accept();
			strL = new StringLiteral(spelling, previousTokenPosition);
		} else {
			syntacticError("string literal expected here", "");
		}
		return strL;
	}
}
