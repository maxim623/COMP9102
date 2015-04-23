/*
 * Parser.java            
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
	public Type typeAST;
	public Ident id;
	public Type_ID(Type _typeAST, Ident _id) {
		typeAST = _typeAST;
		id = _id;
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
			List declList = parseCommonPrefix();
			finish(programPos);
			programAST = new Program(declList, programPos); 
			if (currentToken.kind != Token.EOF) {
				syntacticError("\"%\" unknown type", currentToken.spelling);
			}
		}
		catch (SyntaxError s) { return null; }
		return programAST;
	}

	private List parseCommonPrefix() throws SyntaxError {
		SourcePosition preFixPos = new SourcePosition();
		start(preFixPos);
		List declListAST = null;
		List subList = null;
		Decl funDeclAST = null;
		List varDeclAST = null;
		Type type = null;
		Ident id = null;
		if(typeFirstSet.contains(currentToken.kind)) {
			type = parseType();
			id = parseIdent();
			if(currentToken.kind == Token.LPAREN) {
				funDeclAST = parsePartFuncDecl(type, id);
			} else {
				varDeclAST = parsePartVarDecl(type, id);
			}
		} else {
			return new EmptyDeclList(dummyPos);
		}

		if(typeFirstSet.contains(currentToken.kind)) {
			subList = parseCommonPrefix();
		} else {
			subList = new EmptyDeclList(dummyPos);
		}
		if(funDeclAST != null) {
			finish(preFixPos);
			declListAST = new DeclList(funDeclAST, subList, preFixPos);
		}
		if(varDeclAST != null) {
			// find the EmptyDeclList in current DeclList, and it always the tree node.
			// Connect subAST to this tree node.
			DeclList rightMostDeclListAST = (DeclList)varDeclAST;
			while(!(rightMostDeclListAST.DL instanceof EmptyDeclList)) {
				rightMostDeclListAST = (DeclList) rightMostDeclListAST.DL;
			}
			rightMostDeclListAST.DL = subList;
			declListAST = varDeclAST;
		}
		return declListAST;
	}

	private Decl parsePartFuncDecl(Type type, Ident id) throws SyntaxError {
		SourcePosition funcPos = new SourcePosition();
		start(funcPos);
		List paraListAST = parseParaList();
		Stmt compoundStmtAST = parseCompoundStmt();
		finish(funcPos);
		return new FuncDecl(type, id, paraListAST, compoundStmtAST, funcPos);
	}

	private List parsePartVarDecl(Type type, Ident id) throws SyntaxError {
		SourcePosition varDeclPos = new SourcePosition();
		start(varDeclPos);
		List varDeclAST = null;
		Type declType = null;
		Decl declAST = null;
		if(currentToken.kind == Token.LBRACKET) {
			// this is declarator part
			// array declaration
			accept();
			Expr indexExpr = null;
			if(currentToken.kind == Token.INTLITERAL) {
				IntLiteral index = parseIntLiteral();
				indexExpr = new IntExpr(index, previousTokenPosition);
			} else {
				indexExpr = new EmptyExpr(dummyPos);
			}
			match(Token.RBRACKET);
			declType = new ArrayType(type, indexExpr, varDeclPos);
		} else {
			// global variable declaration
			declType = type;
		}
		SourcePosition initDeclPos = new SourcePosition();
		copyStart(varDeclPos, initDeclPos);
		if(currentToken.kind == Token.EQ) {
			// this is init-declarator part
			accept();
			Expr initExprAST = parseInitialiser();
			finish(initDeclPos);
			declAST = new GlobalVarDecl(declType, id, initExprAST, initDeclPos);

		} else {
			finish(initDeclPos);
			declAST = new GlobalVarDecl(declType, id, new EmptyExpr(dummyPos), initDeclPos);
		}
		if(currentToken.kind == Token.COMMA) {
			// this is init-declarator-list part
			accept();
			// same as before, the arrays and variables declared here must be global variable
			boolean isGlobal = true;
			List subDeclAST = parseInitDeclaratorList(type, isGlobal);
			finish(varDeclPos);
			varDeclAST = new DeclList(declAST, subDeclAST, varDeclPos);
		} else {
			finish(varDeclPos);
			varDeclAST = new DeclList(declAST, new EmptyDeclList(dummyPos), varDeclPos);
		}
		match(Token.SEMICOLON);
		return varDeclAST;
	}

	private List parseVarDecl() throws SyntaxError {
		Type type = parseType();
		// the global variables declaration has been processed in method parsePartVarDecl
		// therefore, all the declarations here must be local variables
		boolean isGlobal = false;
		List declListAST = parseInitDeclaratorList(type, isGlobal);
		match(Token.SEMICOLON);
		return declListAST;
	}

	private List parseInitDeclaratorList(Type type, boolean isGlobal) throws SyntaxError {
		SourcePosition initDeclPos = new SourcePosition();
		start(initDeclPos);
		Decl declAST = parseInitDeclarator(type, isGlobal);
		List declListAST = null;
		if(currentToken.kind == Token.COMMA) {
			accept();
			List subList = parseInitDeclaratorList(type, isGlobal);
			finish(initDeclPos);
			declListAST = new DeclList(declAST, subList, initDeclPos);
		} else {
			finish(initDeclPos);
			declListAST = new DeclList(declAST, new EmptyDeclList(dummyPos), initDeclPos);
		}
		return declListAST;
	}

	private Decl parseInitDeclarator(Type declType, boolean isGlobal) throws SyntaxError {
		SourcePosition initDeclPos = new SourcePosition();
		start(initDeclPos);
		Decl declAST = null;
		Type_ID type_ID = parseDeclarator(declType);
		if(currentToken.kind == Token.EQ) {
			accept();
			Expr initExprAST = parseInitialiser();
			finish(initDeclPos);
			// create different variable declaration according to isGlobal flag
			if(isGlobal) {
				declAST = new GlobalVarDecl(type_ID.typeAST, type_ID.id, initExprAST, initDeclPos);
			} else {
				declAST = new LocalVarDecl(type_ID.typeAST, type_ID.id, initExprAST, initDeclPos);
			}
		} else {
			finish(initDeclPos);
			if(isGlobal) {
				declAST = new GlobalVarDecl(type_ID.typeAST, type_ID.id, new EmptyExpr(dummyPos), initDeclPos);
			} else {
				declAST = new LocalVarDecl(type_ID.typeAST, type_ID.id, new EmptyExpr(dummyPos), initDeclPos);
			}
		}
		return declAST;
	}

	// type information must be passed to here in order to create corresponding expression
	// besides, this method need to disambiguate the declaration is a common variable or a array
	private Type_ID parseDeclarator(Type type) throws SyntaxError {
		SourcePosition declaratorPos = new SourcePosition();
		start(declaratorPos);
		Ident idAST = parseIdent();
		if(currentToken.kind == Token.LBRACKET) {
			accept();
			Expr indexExpr = null;
			if(currentToken.kind == Token.INTLITERAL) {
				IntLiteral intLiteral = parseIntLiteral();
				indexExpr = new IntExpr(intLiteral, previousTokenPosition);
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

	private Expr parseInitialiser() throws SyntaxError {
		SourcePosition initPos = new SourcePosition();
		start(initPos);
		Expr initAST = null;
		if(currentToken.kind == Token.LCURLY) {
			accept();
			List initListAST = parseInitExprList();
			finish(initPos);
			initAST = new InitExpr(initListAST, initPos);
			match(Token.RCURLY);
		} else {
			finish(initPos);
			initAST = parseExpr();
		}
		return initAST;
	}

	// this nonterminal is expr | expr (, expr)*, used only in initialiser
	private List parseInitExprList() throws SyntaxError {
		SourcePosition initPos = new SourcePosition();
		start(initPos);
		Expr exprAST = parseExpr();
		List listAST = null;
		if(currentToken.kind == Token.COMMA) {
			accept();
			List subListAST = parseInitExprList();
			finish(initPos);
			listAST = new ExprList(exprAST, subListAST, initPos);
		} else {
			finish(initPos);
			listAST = new ExprList(exprAST, new EmptyExprList(dummyPos), initPos);
		}
		return listAST;
	}

	//  ======================== TYPES ==========================
	private Type parseType() throws SyntaxError {
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
		accept();
		return typeAST;
	}

	// ======================= STATEMENTS ==============================
	private Stmt parseCompoundStmt() throws SyntaxError {
		SourcePosition stmtPos = new SourcePosition();
		start(stmtPos);
		Stmt cAST = null; 
		match(Token.LCURLY);
		List declListAST = parseVarDeclList();
		List slAST = parseStmtList();
		match(Token.RCURLY);
		finish(stmtPos);
		if (declListAST instanceof EmptyDeclList && slAST instanceof EmptyStmtList) {
			cAST = new EmptyCompStmt(stmtPos);
		}
		else {
			cAST = new CompoundStmt(declListAST, slAST, stmtPos);
		}
		return cAST;
	}

	private List parseVarDeclList() throws SyntaxError {
		SourcePosition declListPos = new SourcePosition();
		start(declListPos);
		List listAST = null;
		if(typeFirstSet.contains(currentToken.kind)) {
			// declaration list appears here locates in compound statements, so it is local declaration.
			listAST = parseVarDecl();
			// find the tree node EmptyDeclList and substitute it with subDeclList
			DeclList rightMostDeclListAST = (DeclList) listAST;
			while(!(rightMostDeclListAST.DL instanceof EmptyDeclList)) {
				rightMostDeclListAST = (DeclList) rightMostDeclListAST.DL;
			}
			rightMostDeclListAST.DL =  parseVarDeclList();
		} else {
			finish(declListPos);
			listAST = new EmptyDeclList(dummyPos);
		}
		return listAST;
	}

	// Here, a new nontermial has been introduced to define { stmt } *
	private List parseStmtList() throws SyntaxError {
		SourcePosition stmtPos = new SourcePosition();
		start(stmtPos);
		List stmtListAST = null; 
		if (currentToken.kind != Token.RCURLY) {
			Stmt stmtAST = parseStmt();
			List subList = null;
			if (currentToken.kind != Token.RCURLY) {
				subList = parseStmtList();
				finish(stmtPos);
				stmtListAST = new StmtList(stmtAST, subList, stmtPos);
			} else {
				finish(stmtPos);
				stmtListAST = new StmtList(stmtAST, new EmptyStmtList(dummyPos), stmtPos);
			}
		}
		else {
			stmtListAST = new EmptyStmtList(dummyPos);
		}
		return stmtListAST;
	}

	private Stmt parseStmt() throws SyntaxError {
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

	private Stmt parseIfStmt() throws SyntaxError {
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
		if(currentToken.kind == Token.ELSE) {
			accept();
			elseAST = parseStmt();
			finish(ifPos);
			ifAST = new IfStmt(condAST, thenAST, elseAST, ifPos);
		} else {
			finish(ifPos);
			ifAST = new IfStmt(condAST, thenAST, ifPos);
		}
		return ifAST;
	}

	private Stmt parseForStmt() throws SyntaxError {
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

	private Stmt parseWhileStmt() throws SyntaxError {
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

	private Stmt parseBreakStmt() throws SyntaxError {
		SourcePosition breakPos = new SourcePosition();
		start(breakPos);
		accept();
		match(Token.SEMICOLON);
		finish(breakPos);
		return new BreakStmt(breakPos);
	}

	private Stmt parseContinueStmt() throws SyntaxError {
		SourcePosition contPos = new SourcePosition();
		start(contPos);
		accept();
		match(Token.SEMICOLON);
		finish(contPos);
		return new ContinueStmt(contPos);
	}

	private Stmt parseReturnStmt() throws SyntaxError {
		SourcePosition retPos = new SourcePosition();
		start(retPos);
		Expr retExprAST = null;
		accept();
		if(exprFirstSet.contains(currentToken.kind)) {
			retExprAST = parseExpr();
		} else {
			retExprAST = new EmptyExpr(dummyPos);
		}
		match(Token.SEMICOLON);
		finish(retPos);
		return new ReturnStmt(retExprAST, retPos);
	}

	private Stmt parseExprStmt() throws SyntaxError {
		SourcePosition stmtPos = new SourcePosition();
		start(stmtPos);
		Stmt sAST = null;
		if (exprFirstSet.contains(currentToken.kind)) {
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

	/*
	 * left recursion
	 * A -> B | A op B
	 * 
	 * A->BA'
	 * A'-> op B | epsilon
	 * 
	 * A->B(op B)*
	 * */
	private Expr parseExpr() throws SyntaxError {
		return parseAssignExpr();
	}

	private Expr parseAssignExpr() throws SyntaxError {
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

	private Expr parseCondOrExpr() throws SyntaxError {
		SourcePosition condOrPos = new SourcePosition();
		start(condOrPos);
		Expr condOrAST = parseCondAndEpxr();
		while(currentToken.kind == Token.OROR) {
			Operator op = acceptOperator();
			Expr subExpr = parseCondAndEpxr();
			SourcePosition subConOrPos = new SourcePosition();
			copyStart(condOrPos, subConOrPos);
			finish(subConOrPos);
			condOrAST = new BinaryExpr(condOrAST, op, subExpr, subConOrPos);
		}
		return condOrAST;
	}

	private Expr parseCondAndEpxr() throws SyntaxError {
		SourcePosition conAndPos = new SourcePosition();
		start(conAndPos);
		Expr condAndAST = parseEqualityExpr();
		while(currentToken.kind == Token.ANDAND) {
			Operator op = acceptOperator();
			Expr subExpr = parseEqualityExpr();
			SourcePosition SubConAndPos = new SourcePosition();
			copyStart(conAndPos, SubConAndPos);
			finish(SubConAndPos);
			condAndAST = new BinaryExpr(condAndAST, op, subExpr, SubConAndPos);
		}
		return condAndAST;
	}

	private Expr parseEqualityExpr() throws SyntaxError {
		SourcePosition eqPos = new SourcePosition();
		start(eqPos);
		Expr eqAST = parseRelExpr();
		while(currentToken.kind == Token.EQEQ || currentToken.kind == Token.NOTEQ) {
			Operator op = acceptOperator();
			Expr subExpr = parseRelExpr();
			SourcePosition subEqPos = new SourcePosition();
			copyStart(eqPos, subEqPos);
			finish(subEqPos);
			eqAST = new BinaryExpr(eqAST, op, subExpr, subEqPos);
		}
		return eqAST;
	}

	private Expr parseRelExpr() throws SyntaxError {
		SourcePosition relExprPos = new SourcePosition();
		start(relExprPos);
		Expr exprAST = parseAdditiveExpr();
		while(currentToken.kind == Token.GT || currentToken.kind == Token.GTEQ ||
				currentToken.kind == Token.LT || currentToken.kind == Token.LTEQ) {
			Operator op = acceptOperator();
			Expr subExpr = parseAdditiveExpr();
			SourcePosition subRelExpr = new SourcePosition();
			copyStart(relExprPos, relExprPos);
			finish(subRelExpr);
			exprAST = new BinaryExpr(exprAST, op, subExpr, subRelExpr);
		}
		return exprAST;
	}

	private Expr parseAdditiveExpr() throws SyntaxError {
		SourcePosition addExprPos = new SourcePosition();
		start(addExprPos);
		Expr exprAST = parseMultiplicativeExpr();
		while (currentToken.kind == Token.PLUS || currentToken.kind == Token.MINUS) {
			Operator op = acceptOperator();
			Expr subExpr = parseMultiplicativeExpr();
			SourcePosition subAddPos = new SourcePosition();
			copyStart(addExprPos, subAddPos);
			finish(subAddPos);
			exprAST = new BinaryExpr(exprAST, op, subExpr, subAddPos);
		}
		return exprAST;
	}

	private Expr parseMultiplicativeExpr() throws SyntaxError {
		SourcePosition multiExprPos = new SourcePosition();
		start(multiExprPos);
		Expr exprAST = parseUnaryExpr();
		while (currentToken.kind == Token.MULT || currentToken.kind == Token.DIV) {
			Operator op = acceptOperator();
			Expr subExpr = parseUnaryExpr();
			SourcePosition submultPos = new SourcePosition();
			copyStart(multiExprPos, submultPos);
			finish(submultPos);
			exprAST = new BinaryExpr(exprAST, op, subExpr, submultPos);
		}
		return exprAST;
	}

	private Expr parseUnaryExpr() throws SyntaxError {
		SourcePosition unaryExprPos = new SourcePosition();
		start(unaryExprPos);
		Expr exprAST = null;
		Operator op = null;
		switch (currentToken.kind) {
		case Token.PLUS:
		case Token.MINUS:
		case Token.NOT:
			Expr subExprAST = null;
			op = acceptOperator();
			subExprAST = parseUnaryExpr();
			finish(unaryExprPos);
			exprAST = new UnaryExpr(op, subExprAST, unaryExprPos);
			break;
		default:
			exprAST = parsePrimaryExpr();
			break;
		}
		return exprAST;
	}

	private Expr parsePrimaryExpr() throws SyntaxError {
		SourcePosition primaryExprPos = new SourcePosition();
		start(primaryExprPos);
		Expr exprAST = null;		
		switch (currentToken.kind) {
		case Token.ID:
			Ident id = parseIdent();
			if(currentToken.kind == Token.LPAREN) {
				SourcePosition callPos = new SourcePosition();
				copyStart(primaryExprPos, callPos);
				List argListAST = parseArgList();
				finish(callPos);
				exprAST = new CallExpr(id, argListAST, callPos);
			} else if (currentToken.kind == Token.LBRACKET){
				Var arrayVar = new SimpleVar(id, previousTokenPosition);
				accept();
				SourcePosition arrayPos = new SourcePosition();
				copyStart(primaryExprPos, arrayPos);
				Expr indexAST = parseExpr();
				match(Token.RBRACKET);
				finish(arrayPos);
				exprAST = new ArrayExpr(arrayVar, indexAST, arrayPos);
			} else {
				finish(primaryExprPos);
				Var var = new SimpleVar(id, primaryExprPos);
				exprAST = new VarExpr(var, primaryExprPos);
			}
			break;
		case Token.LPAREN:
			accept();
			exprAST = parseExpr();
			match(Token.RPAREN);
			finish(primaryExprPos);
			break;
		case Token.INTLITERAL:
			IntLiteral ilLiteral = parseIntLiteral();
			finish(primaryExprPos);
			exprAST = new IntExpr(ilLiteral, primaryExprPos);
			break;
		case Token.FLOATLITERAL:
			FloatLiteral floatLiteral = parseFloatLiteral();
			finish(primaryExprPos);
			exprAST = new FloatExpr(floatLiteral, primaryExprPos);
			break;
		case Token.BOOLEANLITERAL:
			BooleanLiteral booleanLiteral = parseBooleanLiteral();
			finish(primaryExprPos);
			exprAST = new BooleanExpr(booleanLiteral, primaryExprPos);
			break;
		case Token.STRINGLITERAL:
			StringLiteral stringLiteral = parseStringLiteral();
			finish(primaryExprPos);
			exprAST = new StringExpr(stringLiteral, primaryExprPos);
			break;
		default:
			syntacticError("illegal primary expression", currentToken.spelling);
			exprAST = new EmptyExpr(dummyPos);
		}
		return exprAST;
	}

	private List parseParaList() throws SyntaxError {
		SourcePosition paraListPos = new SourcePosition();
		start(paraListPos);
		match(Token.LPAREN);
		List paraListAST = null;
		if(currentToken.kind == Token.RPAREN) {
			accept();
			finish(paraListPos);
			paraListAST = new EmptyParaList(dummyPos);
		} else {
			paraListAST = parseProperParaList();
			match(Token.RPAREN);
			finish(paraListPos);
		}
		return paraListAST;
	}

	private List parseProperParaList() throws SyntaxError {
		SourcePosition properParaListPos = new SourcePosition();
		start(properParaListPos);
		ParaDecl declAST = parseParaDecl();
		List listAST = null;
		if(currentToken.kind == Token.COMMA) {
			accept();
			List subList = parseProperParaList();
			finish(properParaListPos);
			listAST = new ParaList(declAST, subList, properParaListPos);
		} else {
			finish(properParaListPos);
			listAST = new ParaList(declAST, new EmptyParaList(dummyPos), properParaListPos);
		}
		return listAST;
	}

	private ParaDecl parseParaDecl() throws SyntaxError {
		SourcePosition paraDeclPos = new SourcePosition();
		start(paraDeclPos);
		Type type = parseType();
		Type_ID type_ID = parseDeclarator(type);
		finish(paraDeclPos);
		return new ParaDecl(type_ID.typeAST, type_ID.id, paraDeclPos);
	}

	private List parseArgList() throws SyntaxError {
		SourcePosition argListPos = new SourcePosition();
		start(argListPos);
		match(Token.LPAREN);
		List argListAST = null;
		if(currentToken.kind == Token.RPAREN) {
			accept();
			finish(argListPos);
			argListAST = new EmptyArgList(dummyPos);
		} else {
			argListAST = parseProperArgList();
			match(Token.RPAREN);
		}
		return argListAST;
	}

	private List parseProperArgList() throws SyntaxError {
		SourcePosition argListPos = new SourcePosition();
		start(argListPos);
		Arg arg = parseArg();
		List argListAST = null;
		if(currentToken.kind == Token.COMMA) {
			accept();
			// parse the rest of arg list
			List subListAST = parseProperArgList();
			finish(argListPos);
			argListAST = new ArgList(arg, subListAST, argListPos);
		} else {
			finish(argListPos);
			argListAST = new ArgList(arg, new EmptyArgList(dummyPos), argListPos);
		}
		return argListAST;
	}

	private Arg parseArg() throws SyntaxError {
		SourcePosition argPos = new SourcePosition();
		start(argPos);
		Expr exprAST = parseExpr();
		finish(argPos);
		return new Arg(exprAST, argPos);
	}

	// ========================== ID, OPERATOR and LITERALS ========================
	private Ident parseIdent() throws SyntaxError {
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
	private Operator acceptOperator() throws SyntaxError {
		Operator O = null;
		previousTokenPosition = currentToken.position;
		String spelling = currentToken.spelling;
		O = new Operator(spelling, previousTokenPosition);
		currentToken = scanner.getToken();
		return O;
	}

	private IntLiteral parseIntLiteral() throws SyntaxError {
		IntLiteral IL = null;
		if (currentToken.kind == Token.INTLITERAL) {
			String spelling = currentToken.spelling;
			accept();
			IL = new IntLiteral(spelling, previousTokenPosition);
		} else 
			syntacticError("integer literal expected here", "");
		return IL;
	}

	private FloatLiteral parseFloatLiteral() throws SyntaxError {
		FloatLiteral FL = null;
		if (currentToken.kind == Token.FLOATLITERAL) {
			String spelling = currentToken.spelling;
			accept();
			FL = new FloatLiteral(spelling, previousTokenPosition);
		} else 
			syntacticError("float literal expected here", "");
		return FL;
	}

	private BooleanLiteral parseBooleanLiteral() throws SyntaxError {
		BooleanLiteral BL = null;
		if (currentToken.kind == Token.BOOLEANLITERAL) {
			String spelling = currentToken.spelling;
			accept();
			BL = new BooleanLiteral(spelling, previousTokenPosition);
		} else 
			syntacticError("boolean literal expected here", "");
		return BL;
	}

	private StringLiteral parseStringLiteral() throws SyntaxError {
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
