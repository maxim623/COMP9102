/**
 * Checker.java   
 * Sun Apr 26 13:41:38 AEST 2015
 **/

package VC.Checker;

import java.util.Vector;

import VC.ASTs.*;
import VC.Scanner.SourcePosition;
import VC.ErrorReporter;
import VC.StdEnvironment;

public final class Checker implements Visitor {

	private String errMesg[] = {
			"*0: main function is missing",
			"*1: return type of main is not int",

			// defined occurrences of identifiers
			// for global, local and parameters
			"*2: identifier redeclared",
			"*3: identifier declared void",
			"*4: identifier declared void[]",

			// applied occurrences of identifiers
			"*5: identifier undeclared",

			// assignments
			"*6: incompatible type for =",
			"*7: invalid lvalue in assignment",

			// types for expressions
			"*8: incompatible type for return",
			"*9: incompatible type for this binary operator",
			"*10: incompatible type for this unary operator",

			// scalars
			"*11: attempt to use an array/fuction as a scalar",

			// arrays
			"*12: attempt to use a scalar/function as an array",
			"*13: wrong type for element in array initialiser",
			"*14: invalid initialiser: array initialiser for scalar",
			"*15: invalid initialiser: scalar initialiser for array",
			"*16: excess elements in array initialiser",
			"*17: array subscript is not an integer",
			"*18: array size missing",

			// functions
			"*19: attempt to reference a scalar/array as a function",

			// conditional expressions in if, for and while
			"*20: if conditional is not boolean",
			"*21: for conditional is not boolean",
			"*22: while conditional is not boolean",

			// break and continue
			"*23: break must be in a while/for",
			"*24: continue must be in a while/for",

			// parameters
			"*25: too many actual parameters",
			"*26: too few actual parameters",
			"*27: wrong type for actual parameter",

			// reserved for errors that I may have missed (J. Xue)
			"*28: misc 1", "*29: misc 2",

			// the following two checks are optional
			"*30: statement(s) not reached", "*31: missing return statement", };

	private SymbolTable idTable;
	private static SourcePosition dummyPos = new SourcePosition();
	private ErrorReporter reporter;
	private final static Ident dummyI = new Ident("x", dummyPos);
	private Vector<FuncDecl> functionHasRet;

	// Checks whether the source program, represented by its AST,
	// satisfies the language's scope rules and type rules.
	// Also decorates the AST as follows:
	// (1) Each applied occurrence of an identifier is linked to
	// the corresponding declaration of that identifier.
	// (2) Each expression and variable is decorated by its type.
	public Checker(ErrorReporter reporter) {
		this.reporter = reporter;
		this.idTable = new SymbolTable();
		establishStdEnvironment();
		functionHasRet = new Vector<FuncDecl>();
	}

	public void check(AST ast) {
		ast.visit(this, null);
	}

	// auxiliary methods
	private void declareVariable(Ident ident, Decl decl) {
		IdEntry entry = idTable.retrieveOneLevel(ident.spelling);
		if (entry == null) {
			; // no problem
		} else {
			reporter.reportError(errMesg[2] + ": %", ident.spelling, ident.position);
		}
		idTable.insert(ident.spelling, decl);
	}

	/*
	 * check if there is main function or not 
	 * check the return type of main function
	 */
	@Override
	public Object visitProgram(Program program, Object o) {
		program.FL.visit(this, null);
		Decl mainDecl = idTable.retrieve("main");
		if (mainDecl == null || !mainDecl.isFuncDecl()) {
			// no main function
			reporter.reportError(errMesg[0], "", program.position);
		} else {
			if (!((FuncDecl) mainDecl).T.isIntType()) {
				// the return type of main is not integer
				reporter.reportError(errMesg[1], "", mainDecl.position);
			}
		}
		return null;
	}

	/*
	 * check function name redeclaration pass return type to statement list so
	 * that return statement can check return type is compatible or not
	 */
	@Override
	public Object visitFuncDecl(FuncDecl funcDecl, Object o) {
		IdEntry func = idTable.retrieveOneLevel(funcDecl.I.spelling);
		if (func != null) {
			// duplicate function name
			reporter.reportError(errMesg[2] + ": %", func.id, funcDecl.position);
		}
		// although this symbol has been declared, it still need to be push into symbol table to avoid 
		// incorrect information
		idTable.insert(funcDecl.I.spelling, funcDecl);
		/*
		 * Currently parameter list cannot be visited because if parameter list
		 * is visited here, the scope of parameters will be in the same scope as
		 * function. However, the scope of parameters begins from compound
		 * statement to the end of function. So, visiting parameter list should be 
		 * postponed to visiting compound statement.
		 */
		// funcDecl.PL.visit(this, null);
		// pass declaration of function to return statement
		funcDecl.S.visit(this, funcDecl);
		/*
		 * check whether the function with return value has corresponding return statement.
		 * when checker visit return statement, it will put the function declaration with
		 * return statement to a vector. Now check whether current function is in this vector.
		 * If in, that means this function has corresponding return statement.
		 * If not, checker reports error that function has no return statement.
		 * */
		if (!funcDecl.T.isVoidType() && !functionHasRet.contains(funcDecl)) {
			reporter.reportError(errMesg[31] + ": function % need a correct return statement.", funcDecl.I.spelling, funcDecl.position);
		}
		return null;
	}

	@Override
	public Object visitDeclList(DeclList ast, Object o) {
		ast.D.visit(this, null);
		ast.DL.visit(this, null);
		return null;
	}

	// what is the different between global variable declaration and local?
	@Override
	public Object visitGlobalVarDecl(GlobalVarDecl globalVarDecl, Object o) {
		declareVariable(globalVarDecl.I, globalVarDecl);
		if (globalVarDecl.T.isVoidType()) {
			// declaration cannot be void type
			reporter.reportError(errMesg[3] + ": variable % declared as void", globalVarDecl.I.spelling, globalVarDecl.position);
		}
		if (globalVarDecl.T.isArrayType()) {
			ArrayType arrayType = (ArrayType) globalVarDecl.T;
			if (arrayType.T.isVoidType()) {
				// array cannot be void type
				reporter.reportError(errMesg[4] + ": array % declared as void", globalVarDecl.I.spelling,globalVarDecl.position);
			}
			if (arrayType.E.isEmptyExpr() && !(globalVarDecl.E instanceof InitExpr)) {
				// array length is not specified explicitly and without array initialization list
				// the length of array cannot be deduced
				reporter.reportError(errMesg[18] + ": cannot determined the length of array %", 
						globalVarDecl.I.spelling, globalVarDecl.position);
			}
		}

		Object exprTypeOrArrayLength = globalVarDecl.E.visit(this,globalVarDecl.T);
		if (globalVarDecl.T.isArrayType()) {
			if (globalVarDecl.E instanceof InitExpr) {
				Integer initLength = (Integer) exprTypeOrArrayLength;
				if (((ArrayType) globalVarDecl.T).E.isEmptyExpr()) {
					// use the length of initializer as the length of array
					((ArrayType) globalVarDecl.T).E = new IntExpr(new IntLiteral(initLength.toString(), dummyPos),dummyPos);
				} else {
					Integer declarLength = Integer.parseInt(((IntExpr) ((ArrayType) globalVarDecl.T).E).IL.spelling);
					if (declarLength < initLength) {
						// length of array init is larger than array size
						reporter.reportError(errMesg[16] + ": array %", globalVarDecl.I.spelling, globalVarDecl.E.position);
					}
				}
			} else if (!globalVarDecl.E.isEmptyExpr()) {
				// use not a array initializer to initialize array
				reporter.reportError(errMesg[15] + ": %", globalVarDecl.I.spelling, globalVarDecl.position);
			}
		} else {
			if (globalVarDecl.T.assignable(globalVarDecl.E.type)) {
				if (!globalVarDecl.T.equals(globalVarDecl.E.type)) {
					globalVarDecl.E = i2f(globalVarDecl.E);
				}
			} else {
				// bad initializer
				reporter.reportError(errMesg[6], "", globalVarDecl.E.position);
			}
		}
		return null;
	}

	public Object visitLocalVarDecl(LocalVarDecl localVarDecl, Object o) {
		declareVariable(localVarDecl.I, localVarDecl);
		if (localVarDecl.T.isVoidType()) {
			// declaration cannot be void type
			reporter.reportError(errMesg[3] + ": variable % declared as void", localVarDecl.I.spelling, localVarDecl.position);
		}
		if (localVarDecl.T.isArrayType()) {
			if (((ArrayType) localVarDecl.T).T.isVoidType()) {
				// array cannot be void type
				reporter.reportError(errMesg[4] + ": array % declared as void", localVarDecl.I.spelling,localVarDecl.position);
			}
			if (((ArrayType) localVarDecl.T).E.isEmptyExpr()
					&& !(localVarDecl.E instanceof InitExpr)) {
				// array length is not specified explicitly and without array
				// initialization list
				// the length of array cannot be deduced
				reporter.reportError(errMesg[18] + ": cannot determined the length of array %", 
						localVarDecl.I.spelling, localVarDecl.position);
			}
		}
		// pass the variable declaration type to initializer
		Object exprTypeOrInitLength = localVarDecl.E.visit(this, localVarDecl.T);
		if (localVarDecl.T.isArrayType()) {
			if (localVarDecl.E instanceof InitExpr) {
				Integer initListLength = (Integer) exprTypeOrInitLength;
				if (((ArrayType) localVarDecl.T).E.isEmptyExpr()) {
					// use the length of initializer as the length of array
					((ArrayType) localVarDecl.T).E = new IntExpr(new IntLiteral(initListLength.toString(), dummyPos),dummyPos);
				} else {
					Integer size = Integer.parseInt(((IntExpr) ((ArrayType) localVarDecl.T).E).IL.spelling);
					if (size < initListLength) {
						// length of array init is larger than array size
						reporter.reportError(errMesg[16] + ": array %", localVarDecl.I.spelling, localVarDecl.E.position);
					}
				}
			} else if (!localVarDecl.E.isEmptyExpr()) {
				// use not a array initializer to initialize array
				reporter.reportError(errMesg[15] + ": %", localVarDecl.I.spelling, localVarDecl.position);
			}
		} else {
			if (localVarDecl.T.assignable(localVarDecl.E.type)) {
				if (!localVarDecl.T.equals(localVarDecl.E.type)) {
					localVarDecl.E = i2f(localVarDecl.E);
				}
			} else {
				// bad initializer
				reporter.reportError(errMesg[6], "", localVarDecl.E.position);
			}
		}
		return null;
	}

	// Statements
	@Override
	public Object visitIfStmt(IfStmt ifStmt, Object o) {
		Type exprType = (Type) ifStmt.E.visit(this, null);
		if (exprType == null || !exprType.isBooleanType()) {
			// not a boolean expression
			reporter.reportError(errMesg[20], "", ifStmt.E.position);
		}
		// Object o is the declaration of function that this statement belongs to
		ifStmt.S1.visit(this, o);
		ifStmt.S2.visit(this, o);
		return null;
	}

	@Override
	public Object visitForStmt(ForStmt forStmt, Object o) {
		forStmt.E1.visit(this, null);
		Type exprType = (Type) forStmt.E2.visit(this, null);
		if (exprType == null || !forStmt.E2.isEmptyExpr() && !exprType.isBooleanType()) {
			// not a boolean expression
			reporter.reportError(errMesg[21], "", forStmt.E2.position);
		}
		forStmt.E3.visit(this, null);
		forStmt.S.visit(this, o);
		return null;
	}

	@Override
	public Object visitWhileStmt(WhileStmt whileStmt, Object o) {
		Type exprType = (Type) whileStmt.E.visit(this, null);
		if (exprType == null || !exprType.isBooleanType()) {
			// not a boolean expression
			reporter.reportError(errMesg[22], "", whileStmt.position);
		}
		whileStmt.S.visit(this, o);
		return null;
	}

	// find whether break is in while or for statements along parent pointer
	private boolean isInWhileOrFor(AST currentParent) {
		if(currentParent == null) {
			return false;
		}
		boolean isFound = false;
		while (true) {
			if (currentParent instanceof WhileStmt || currentParent instanceof ForStmt) {
				isFound = true;
				break;
			} else {
				currentParent = currentParent.parent;
				if(currentParent == null) {
					break;
				}
			}
		}
		return isFound;
	}

	@Override
	public Object visitBreakStmt(BreakStmt breakStmt, Object o) {
		boolean isFound = isInWhileOrFor(breakStmt.parent);
		if (!isFound) {
			reporter.reportError(errMesg[23], "", breakStmt.position);
		}
		return null;
	}

	@Override
	public Object visitContinueStmt(ContinueStmt continueStmt, Object o) {
		boolean isFound = isInWhileOrFor(continueStmt.parent);
		if (!isFound) {
			reporter.reportError(errMesg[24], "", continueStmt.position);
		}
		return null;
	}

	// check function return type is compatible with the type of return
	// statement
	// Object o is the declaration of function
	@Override
	public Object visitReturnStmt(ReturnStmt retStmt, Object o) {
		Type funcRetType = ((FuncDecl) o).T;
		Type retExprType = (Type) retStmt.E.visit(this, null);
		boolean hasCorrectRet = false;
		// xor logic
		if (funcRetType.isVoidType() ^ retStmt.E.isEmptyExpr()) {
			reporter.reportError(errMesg[8] + ": Declared return type is %", funcRetType.toString(), retStmt.position);
		}
		if (!funcRetType.isVoidType()) {
			if (funcRetType.assignable(retExprType)) {
				hasCorrectRet = true;
				if (!funcRetType.equals(retExprType)) {
					retStmt.E = i2f(retStmt.E);
				}
			} else {
				reporter.reportError(errMesg[8] + ": Declared return type is %", funcRetType.toString(), retStmt.position);
				hasCorrectRet = false;
			}
		}
		if(hasCorrectRet) {
			// that means this function does not have return statement or it has incorrect return statement
			functionHasRet.add((FuncDecl) o);
		}
		return null;
	}

	@Override
	public Object visitCompoundStmt(CompoundStmt compoundStmt, Object o) {
		idTable.openScope();
		if (o instanceof FuncDecl) {
			FuncDecl funcDecl = (FuncDecl) o;
			// visit parameter list here so that variables declared in parameter
			// list will be
			// in the scope of compound statement
			funcDecl.PL.visit(this, null);
		}
		compoundStmt.DL.visit(this, null);
		// Object o is declaration of function
		compoundStmt.SL.visit(this, o);
		idTable.closeScope();
		return null;
	}

	@Override
	public Object visitStmtList(StmtList ast, Object o) {
		ast.S.visit(this, o);
		if (ast.S instanceof ReturnStmt && !ast.SL.isEmptyStmtList()) {
			// Unreachable statements after return statement
			reporter.reportError(errMesg[30], "", ast.SL.position);
		}
		ast.SL.visit(this, o);
		return null;
	}

	// Object o is the declaration of function to which expression statement belongs
	@Override
	public Object visitExprStmt(ExprStmt ast, Object o) {
		ast.E.visit(this, o);
		return null;
	}

	@Override
	public Object visitEmptyCompStmt(EmptyCompStmt ast, Object o) {
		return null;
	}

	public Object visitEmptyStmt(EmptyStmt ast, Object o) {
		return null;
	}

	@Override
	public Object visitEmptyExprList(EmptyExprList ast, Object o) {
		return null;
	}

	public Object visitEmptyDeclList(EmptyDeclList ast, Object o) {
		return null;
	}

	public Object visitEmptyStmtList(EmptyStmtList ast, Object o) {
		return null;
	}

	public Object visitEmptyParaList(EmptyParaList ast, Object o) {
		return null;
	}

	public Object visitIntExpr(IntExpr ast, Object o) {
		ast.type = StdEnvironment.intType;
		return ast.type;
	}

	public Object visitFloatExpr(FloatExpr ast, Object o) {
		ast.type = StdEnvironment.floatType;
		return ast.type;
	}

	@Override
	public Object visitBooleanExpr(BooleanExpr ast, Object o) {
		ast.type = StdEnvironment.booleanType;
		return ast.type;
	}

	@Override
	public Object visitStringExpr(StringExpr ast, Object o) {
		ast.type = StdEnvironment.stringType;
		return ast.type;
	}

	/*
	 * in VC, unary operators includes +, - and ! + and - can be apply to
	 * integer and float, and ! can be apply to boolean type and ! need to be
	 * converted to i!
	 */
	@Override
	public Object visitUnaryExpr(UnaryExpr unaryExpr, Object o) {
		Type exprType = (Type) unaryExpr.E.visit(this, null);
		String op = unaryExpr.O.spelling;
		if (op.equals("+") || op.equals("-")) {
			if (exprType.isIntType() || exprType.isFloatType()) {
				unaryExpr.type = exprType;
			} else {
				// apply + and - to wrong type
				reporter.reportError(errMesg[10] + ": incompatible type %", exprType.toString(), unaryExpr.E.position);
				unaryExpr.type = StdEnvironment.errorType;
			}
		}
		if (op.equals("!")) {
			if (exprType.isBooleanType()) {
				unaryExpr.O.spelling = "i" + unaryExpr.O.spelling;
				unaryExpr.type = exprType;
			} else {
				// apply ! to wrong type
				reporter.reportError(errMesg[10] + ": incompatible type % here", exprType.toString(), unaryExpr.E.position);
				unaryExpr.type = StdEnvironment.errorType;
			}
		}
		// apply operator overloading
		if(unaryExpr.type.isFloatType()) {
			unaryExpr.O.spelling = "f" + unaryExpr.O.spelling;
		} else {
			unaryExpr.O.spelling = "i" + unaryExpr.O.spelling;
		}
		return unaryExpr.type;
	}

	/*
	 * check the compatibility between two operands apply type coercion apply
	 * operator overloading
	 */
	@Override
	public Object visitBinaryExpr(BinaryExpr binaryExpr, Object o) {
		Type e1Type = (Type) binaryExpr.E1.visit(this, null);
		Type e2Type = (Type) binaryExpr.E2.visit(this, null);
		if (e1Type.isErrorType() || e2Type.isErrorType()) {
			binaryExpr.type = StdEnvironment.errorType;
		} else if (e1Type.isFloatType() && e2Type.isIntType()) {
			binaryExpr.E2 = i2f(binaryExpr.E2);
			binaryExpr.type = StdEnvironment.floatType;
		} else if (e1Type.isIntType() && e2Type.isFloatType()) {
			binaryExpr.E1 = i2f(binaryExpr.E1);
			binaryExpr.type = StdEnvironment.floatType;
		} else if(e1Type.isIntType() && e2Type.isIntType()) {
			binaryExpr.type = StdEnvironment.intType;
		} else if(e1Type.isFloatType() && e2Type.isFloatType()) {
			binaryExpr.type = StdEnvironment.floatType;
		} else if(e1Type.isBooleanType() && e2Type.isBooleanType()) {
			binaryExpr.type = StdEnvironment.booleanType;
		} else {
			reporter.reportError(errMesg[9] + ": incompatible type %", binaryExpr.O.spelling, binaryExpr.O.position);
			binaryExpr.type = StdEnvironment.errorType;
		}
		boolean convert2IntOp = false;
		boolean convert2FloatOp = false;
		boolean reportError = false;
		// next apply operator overloading
		if (!binaryExpr.type.isErrorType()) {
			String op = binaryExpr.O.spelling;
			if (op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/")) {
				if (binaryExpr.type.isIntType()) {
					convert2IntOp = true;
				} else if (binaryExpr.type.isFloatType()) {
					convert2FloatOp = true;
				} else {
					reportError = true;
				}
			}
			if(op.equals(">") || op.equals(">=") || op.equals("<") || op.equals("<=")) {
				if (binaryExpr.type.isIntType()) {
					convert2IntOp = true;
					binaryExpr.type = StdEnvironment.booleanType;
				} else if (binaryExpr.type.isFloatType()) {
					convert2FloatOp = true;
					binaryExpr.type = StdEnvironment.booleanType;
				} else {
					reportError = true;
				}
			}
			if (op.equals("&&") || op.equals("||")) {
				if (binaryExpr.type.isBooleanType()) {
					convert2IntOp = true;
				} else {
					reportError = true;
				}
			}
			if (op.equals("==") || op.equals("!=")) {
				if (binaryExpr.type.isIntType()	|| binaryExpr.type.isBooleanType()) {
					convert2IntOp = true;
					binaryExpr.type = StdEnvironment.booleanType;
				} else if (binaryExpr.type.isFloatType()) {
					convert2FloatOp = true;
					binaryExpr.type = StdEnvironment.booleanType;
				} else {
					reportError = true;
				}
			}
		}
		if (convert2IntOp) {
			binaryExpr.O.spelling = "i" + binaryExpr.O.spelling;
		}
		if (convert2FloatOp) {
			binaryExpr.O.spelling = "f" + binaryExpr.O.spelling;
		}
		if (reportError) {
			reporter.reportError(errMesg[9] + ": incompatible type %", binaryExpr.O.spelling, binaryExpr.O.position);
			binaryExpr.type = StdEnvironment.errorType;
		}
		return binaryExpr.type;
	}

	/*
	 * Object o is the type of parameter if o is not array type, report error
	 * return value is the length of init list
	 */
	@Override
	public Object visitInitExpr(InitExpr initExpr, Object o) {
		Type declType = (Type) o;
		if (!declType.isArrayType()) {
			// array initializer for scalar
			reporter.reportError(errMesg[14], "", initExpr.position);
			initExpr.type = StdEnvironment.errorType;
			return initExpr.type;
		}
		initExpr.type = declType;
		return initExpr.IL.visit(this, ((ArrayType) declType).T);
	}

	/*
	 * array initialization list. TODO: type check, type coercion, calculate
	 * size Object o is the type of array element return the length of
	 * expression list
	 */
	@Override
	public Object visitExprList(ExprList exprList, Object o) {
		Type elementTpye = (Type) o;
		exprList.E.visit(this, null);
		if (elementTpye.assignable(exprList.E.type)) {
			if (!elementTpye.equals(exprList.E.type)) {
				exprList.E = i2f(exprList.E);
			}
		} else {
			reporter.reportError(errMesg[13], "", exprList.E.position);
		}
		// calculate the lenght of expression list
		if (exprList.EL.isEmpty()) {
			return new Integer(exprList.index + 1);
		} else {
			((ExprList)exprList.EL).index = exprList.index + 1;
			return (Integer) exprList.EL.visit(this, o);
		}
	}

	// check whether the variable is declared as a array
	// check index expression is integer or not
	@Override
	public Object visitArrayExpr(ArrayExpr arrayExpr, Object o) {
		Type varType = (Type) arrayExpr.V.visit(this, null);
		arrayExpr.type = StdEnvironment.errorType;
		if (!varType.isArrayType()) {
			// variable not declared as array
			reporter.reportError(errMesg[12] + ": % is not an array", ((SimpleVar) arrayExpr.V).I.spelling, arrayExpr.V.position);
		} else {
			arrayExpr.type = ((ArrayType) varType).T;
		}
		Type exprType = (Type) arrayExpr.E.visit(this, null);
		if (!exprType.isIntType()) {
			// index expression is not a integer
			reporter.reportError(errMesg[17] + ": index of array % is not integer",
					((SimpleVar) arrayExpr.V).I.spelling, arrayExpr.E.position);
		}
		return arrayExpr.type;
	}

	@Override
	public Object visitVarExpr(VarExpr varExpr, Object o) {
		varExpr.type = (Type) varExpr.V.visit(this, o);
		return varExpr.type;
	}

	@Override
	public Object visitCallExpr(CallExpr call, Object o) {
		Decl funcDecl = idTable.retrieve(call.I.spelling);
		if (funcDecl == null) {
			// cannot find symbol
			reporter.reportError(errMesg[5] + ": % is undeclared", call.I.spelling, call.position);
			call.type = StdEnvironment.errorType;
		} else if (funcDecl.isFuncDecl()) {
			// fetch formal parameter list from function declaration and pass it
			// to actual parameters
			call.AL.visit(this, ((FuncDecl) funcDecl).PL);
			call.type = funcDecl.T;
		} else {
			// use scalar or array as a function
			reporter.reportError(errMesg[19] + ": % is not a function", call.I.spelling, call.position);
			call.type = StdEnvironment.errorType;
		}
		return call.type;
	}

	//
	@Override
	public Object visitAssignExpr(AssignExpr assignExpr, Object o) {
		assignExpr.E1.visit(this, o);
		assignExpr.E2.visit(this, o);
		if (!(assignExpr.E1 instanceof VarExpr || assignExpr.E1 instanceof ArrayExpr)) {
			// lvalue of assignment can just be variable expression or array expression
			reporter.reportError(errMesg[7], "", assignExpr.E1.position);
			assignExpr.type = StdEnvironment.errorType;
		} else if (assignExpr.E1 instanceof VarExpr) {
			Decl decl = idTable.retrieve(((SimpleVar)((VarExpr)assignExpr.E1).V).I.spelling);
			if (decl instanceof FuncDecl) {
				// function cannot be assigned
				reporter.reportError(errMesg[7] + ": % is declared as a function", decl.I.spelling, assignExpr.E1.position);
				assignExpr.type = StdEnvironment.errorType;
			}
			// here we do not need to check the lvalue is an array since this is checked in visitSimpleVar
		}
		// check type compability between E1 and E2
		if (assignExpr.E1.type.assignable(assignExpr.E2.type)) {
			if (!assignExpr.E1.type.equals(assignExpr.E2.type)) {
				assignExpr.E2 = i2f(assignExpr.E2);
				assignExpr.type = StdEnvironment.floatType;
			}
			assignExpr.type = assignExpr.E1.type;
		} else {
			// type is incompatible
			reporter.reportError(errMesg[6], "", assignExpr.E1.position);
			assignExpr.type = StdEnvironment.errorType;
		}
		return assignExpr.type;
	}

	@Override
	public Object visitEmptyExpr(EmptyExpr emptyExpr, Object o) {
		if (emptyExpr.parent instanceof ReturnStmt) {
			emptyExpr.type = StdEnvironment.voidType;
		} else {
			emptyExpr.type = StdEnvironment.errorType;
		}
		return emptyExpr.type;
	}

	// Literals, Identifiers and Operators
	@Override
	public Object visitIntLiteral(IntLiteral IL, Object o) {
		return StdEnvironment.intType;
	}

	@Override
	public Object visitFloatLiteral(FloatLiteral IL, Object o) {
		return StdEnvironment.floatType;
	}

	@Override
	public Object visitBooleanLiteral(BooleanLiteral SL, Object o) {
		return StdEnvironment.booleanType;
	}

	@Override
	public Object visitStringLiteral(StringLiteral IL, Object o) {
		return StdEnvironment.stringType;
	}

	@Override
	public Object visitIdent(Ident I, Object o) {
		Decl binding = idTable.retrieve(I.spelling);
		if (binding != null) {
			I.decl = binding;
		}
		return binding;
	}

	@Override
	public Object visitOperator(Operator O, Object o) {
		return null;
	}

	// Parameters
	@Override
	public Object visitParaList(ParaList ast, Object o) {
		ast.P.visit(this, null);
		ast.PL.visit(this, null);
		return null;
	}

	// check formal parameters
	@Override
	public Object visitParaDecl(ParaDecl ast, Object o) {
		declareVariable(ast.I, ast);
		if (ast.T.isVoidType()) {
			// formal parameter cannot be void type
			reporter.reportError(errMesg[3] + ": % cannot be void", ast.I.spelling, ast.I.position);
		} else if (ast.T.isArrayType()) {
			if (((ArrayType) ast.T).T.isVoidType()) {
				// formal parameter can be array type, but array cannot be void
				// type
				reporter.reportError(errMesg[4] + ": % cannot be void", ast.I.spelling, ast.I.position);
			}
		}
		return null;
	}

	// Arguments
	/*
	 * check type of actual parameters and formal parameters are compatible or
	 * not object o is the formal parameter list
	 */
	@Override
	public Object visitArgList(ArgList argList, Object o) {
		List formalParaList = (List) o;
		/*
		 * If this function is invoked, that means this function invocation has
		 * at least one actual argument. If formal parameter list is an empty
		 * list, report too many actual argument here.
		 */
		if (formalParaList.isEmptyParaList()) {
			// too many actual arguments
			reporter.reportError(errMesg[25], "", argList.position);
		} else {
			argList.A.visit(this, ((ParaList) formalParaList).P);
			argList.AL.visit(this, ((ParaList) formalParaList).PL);
		}
		return null;
	}

	/*
	 * check the compatibility between the type of formal parameter and actual
	 * argument. Object o is a formal parameter. If formal parameter and actual
	 * argument are array type, the type of array should be assignable.
	 */
	@Override
	public Object visitArg(Arg arg, Object o) {
		Decl formalParam = (Decl) o;
		Type formalType = formalParam.T;
		Type actualType = (Type) arg.E.visit(this, null);
		boolean isMatch = false;
		if (formalType.isArrayType()) {
			if (actualType.isArrayType()) {
				Type formalArrayType = ((ArrayType) formalType).T;
				Type actualArrayType = ((ArrayType) actualType).T;
				if (formalArrayType.assignable(actualArrayType)) {
					isMatch = true;
				}
			}
		} else {
			if (formalType.assignable(actualType)) {
				isMatch = true;
			}
		}
		if (!isMatch) {
			reporter.reportError(errMesg[27] + ": % expect here", formalParam.T.toString(), arg.E.position);
		}
		if (isMatch && !formalType.equals(actualType)) {
			arg.E = i2f(arg.E);
		}
		return null;
	}

	/*
	 * Check the too few actual argument object o is formal parameter list. If
	 * it is not empty, report error.
	 */
	@Override
	public Object visitEmptyArgList(EmptyArgList emptyArgList, Object o) {
		List formalParaList = (List) o;
		if (!formalParaList.isEmptyParaList()) {
			// too few actual parameters
			reporter.reportError(errMesg[26], "", emptyArgList.parent.position);
		}
		return null;
	}

	@Override
	public Object visitVoidType(VoidType ast, Object o) {
		return StdEnvironment.voidType;
	}

	@Override
	public Object visitBooleanType(BooleanType ast, Object o) {
		return StdEnvironment.booleanType;
	}

	@Override
	public Object visitIntType(IntType ast, Object o) {
		return StdEnvironment.intType;
	}

	@Override
	public Object visitFloatType(FloatType ast, Object o) {
		return StdEnvironment.floatType;
	}

	@Override
	public Object visitStringType(StringType ast, Object o) {
		return StdEnvironment.stringType;
	}

	@Override
	public Object visitArrayType(ArrayType array, Object o) {
		return array;
	}

	@Override
	public Object visitErrorType(ErrorType ast, Object o) {
		return StdEnvironment.errorType;
	}

	/*
	 * variable should be declared variable cannot be a function name array name
	 * cannot be used alone except that it is a function actual argument
	 */
	@Override
	public Object visitSimpleVar(SimpleVar simpleVar, Object o) {
		Decl decl = idTable.retrieve(simpleVar.I.spelling);
		simpleVar.type = StdEnvironment.errorType;
		if (decl == null) {
			// undeclared identifier
			reporter.reportError(errMesg[5] + ": % is undeclared", simpleVar.I.spelling, simpleVar.I.position);
		} else if (decl instanceof FuncDecl) {
			// identifier collides with function name
			reporter.reportError(errMesg[11] + ": % is not a scalar", simpleVar.I.spelling, simpleVar.I.position);
		} else {
			simpleVar.type = decl.T;
		}
		// if array name are not used as a actual argument
		if (simpleVar.type.isArrayType() && simpleVar.parent instanceof VarExpr && !(simpleVar.parent.parent instanceof Arg)) {
			reporter.reportError(errMesg[11] + ": % is not a scalar", decl.I.spelling, simpleVar.position);
		}
		return simpleVar.type;
	}

	// Creates a small AST to represent the "declaration" of each built-in
	// function, and enters it in the symbol table.
	private FuncDecl declareStdFunc(Type resultType, String id, List pl) {
		FuncDecl binding;
		binding = new FuncDecl(resultType, new Ident(id, dummyPos), pl,	new EmptyStmt(dummyPos), dummyPos);
		idTable.insert(id, binding);
		return binding;
	}

	private Expr i2f(Expr currentExpr) {
		Expr newExpr = new UnaryExpr(new Operator("i2f", currentExpr.position),
				currentExpr, currentExpr.position);
		newExpr.type = StdEnvironment.floatType;
		newExpr.parent = currentExpr.parent;
		currentExpr.parent = newExpr;
		return newExpr;
	}

	// Creates small ASTs to represent "declarations" of all
	// build-in functions.
	// Inserts these "declarations" into the symbol table.
	private void establishStdEnvironment() {
		// Define four primitive types
		// errorType is assigned to ill-typed expressions
		StdEnvironment.booleanType = new BooleanType(dummyPos);
		StdEnvironment.intType = new IntType(dummyPos);
		StdEnvironment.floatType = new FloatType(dummyPos);
		StdEnvironment.stringType = new StringType(dummyPos);
		StdEnvironment.voidType = new VoidType(dummyPos);
		StdEnvironment.errorType = new ErrorType(dummyPos);
		// enter into the declarations for built-in functions into the table
		StdEnvironment.getIntDecl = declareStdFunc(StdEnvironment.intType,
				"getInt", new EmptyParaList(dummyPos));
		StdEnvironment.putIntDecl = declareStdFunc(StdEnvironment.voidType,
				"putInt", new ParaList(new ParaDecl(StdEnvironment.intType,
						dummyI, dummyPos), new EmptyParaList(dummyPos),
						dummyPos));
		StdEnvironment.putIntLnDecl = declareStdFunc(StdEnvironment.voidType,
				"putIntLn", new ParaList(new ParaDecl(StdEnvironment.intType,
						dummyI, dummyPos), new EmptyParaList(dummyPos),
						dummyPos));
		StdEnvironment.getFloatDecl = declareStdFunc(StdEnvironment.floatType,
				"getFloat", new EmptyParaList(dummyPos));
		StdEnvironment.putFloatDecl = declareStdFunc(StdEnvironment.voidType,
				"putFloat", new ParaList(new ParaDecl(StdEnvironment.floatType,
						dummyI, dummyPos), new EmptyParaList(dummyPos),
						dummyPos));
		StdEnvironment.putFloatLnDecl = declareStdFunc(StdEnvironment.voidType,
				"putFloatLn", new ParaList(new ParaDecl(
						StdEnvironment.floatType, dummyI, dummyPos),
						new EmptyParaList(dummyPos), dummyPos));
		StdEnvironment.putBoolDecl = declareStdFunc(StdEnvironment.voidType,
				"putBool", new ParaList(new ParaDecl(
						StdEnvironment.booleanType, dummyI, dummyPos),
						new EmptyParaList(dummyPos), dummyPos));
		StdEnvironment.putBoolLnDecl = declareStdFunc(StdEnvironment.voidType,
				"putBoolLn", new ParaList(new ParaDecl(
						StdEnvironment.booleanType, dummyI, dummyPos),
						new EmptyParaList(dummyPos), dummyPos));
		StdEnvironment.putStringLnDecl = declareStdFunc(
				StdEnvironment.voidType, "putStringLn", new ParaList(
						new ParaDecl(StdEnvironment.stringType, dummyI,
								dummyPos), new EmptyParaList(dummyPos),
								dummyPos));
		StdEnvironment.putStringDecl = declareStdFunc(StdEnvironment.voidType,
				"putString", new ParaList(new ParaDecl(
						StdEnvironment.stringType, dummyI, dummyPos),
						new EmptyParaList(dummyPos), dummyPos));
		StdEnvironment.putLnDecl = declareStdFunc(StdEnvironment.voidType,
				"putLn", new EmptyParaList(dummyPos));
	}
}
