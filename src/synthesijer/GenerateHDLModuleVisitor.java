package synthesijer;

import java.util.Hashtable;

import synthesijer.ast.Method;
import synthesijer.ast.Module;
import synthesijer.ast.Statement;
import synthesijer.ast.SynthesijerAstVisitor;
import synthesijer.ast.Type;
import synthesijer.ast.Variable;
import synthesijer.ast.expr.ArrayAccess;
import synthesijer.ast.expr.AssignExpr;
import synthesijer.ast.expr.AssignOp;
import synthesijer.ast.expr.BinaryExpr;
import synthesijer.ast.expr.FieldAccess;
import synthesijer.ast.expr.Ident;
import synthesijer.ast.expr.Literal;
import synthesijer.ast.expr.MethodInvocation;
import synthesijer.ast.expr.NewArray;
import synthesijer.ast.expr.NewClassExpr;
import synthesijer.ast.expr.ParenExpr;
import synthesijer.ast.expr.SynthesijerExprVisitor;
import synthesijer.ast.expr.TypeCast;
import synthesijer.ast.expr.UnaryExpr;
import synthesijer.ast.statement.BlockStatement;
import synthesijer.ast.statement.BreakStatement;
import synthesijer.ast.statement.ContinueStatement;
import synthesijer.ast.statement.ExprStatement;
import synthesijer.ast.statement.ForStatement;
import synthesijer.ast.statement.IfStatement;
import synthesijer.ast.statement.ReturnStatement;
import synthesijer.ast.statement.SkipStatement;
import synthesijer.ast.statement.SwitchStatement;
import synthesijer.ast.statement.SwitchStatement.Elem;
import synthesijer.ast.statement.SynchronizedBlock;
import synthesijer.ast.statement.TryStatement;
import synthesijer.ast.statement.VariableDecl;
import synthesijer.ast.statement.WhileStatement;
import synthesijer.ast.type.ArrayType;
import synthesijer.ast.type.ComponentType;
import synthesijer.ast.type.MySelfType;
import synthesijer.ast.type.PrimitiveTypeKind;
import synthesijer.hdl.HDLExpr;
import synthesijer.hdl.HDLModule;
import synthesijer.hdl.HDLOp;
import synthesijer.hdl.HDLPort;
import synthesijer.hdl.HDLPrimitiveType;
import synthesijer.hdl.HDLSequencer;
import synthesijer.hdl.HDLSignal;
import synthesijer.hdl.HDLType;
import synthesijer.hdl.HDLUserDefinedType;
import synthesijer.hdl.expr.HDLConstant;
import synthesijer.hdl.expr.HDLValue;
import synthesijer.model.State;
import synthesijer.model.Statemachine;
import synthesijer.model.StatemachineVisitor;
import synthesijer.model.Transition;

public class GenerateHDLModuleVisitor implements SynthesijerAstVisitor{
	
	final HDLModule module;
	final Hashtable<State, HDLSequencer.SequencerState> stateTable;
	final Hashtable<Method, HDLPort> methodReturnTable;
	
	public GenerateHDLModuleVisitor(HDLModule m){
		this.module = m;
		this.stateTable = new Hashtable<State, HDLSequencer.SequencerState>();
		this.methodReturnTable = new Hashtable<Method, HDLPort>();
	}
	
	@Override
	public void visitMethod(Method o) {
		for(VariableDecl v: o.getArgs()){
			HDLType t = getHDLType(v.getType());
			if(t != null) module.newPort(v.getName(), HDLPort.DIR.IN, t);
		}
		HDLType t = getHDLType(o.getType());
		if(t != null){
			HDLPort p = module.newPort(o.getName() + "_return", HDLPort.DIR.OUT, t);
			methodReturnTable.put(o, p);
		}
		HDLPort req = module.newPort(o.getName() + "_req", HDLPort.DIR.IN, HDLPrimitiveType.genBitType());
		HDLPort busy = module.newPort(o.getName() + "_busy", HDLPort.DIR.OUT, HDLPrimitiveType.genBitType());
		o.getStateMachine().accept(new Statemachine2HDLSequencerVisitor(this, req, busy));
		o.getBody().accept(this);
	}
	
	private HDLType getHDLType(Type type){
		if(type instanceof PrimitiveTypeKind){
			return ((PrimitiveTypeKind)type).getHDLType();
		}else if(type instanceof ArrayType){
			System.err.println("unsupported type: " + type);
			return null;
		}else if(type instanceof ComponentType){
			System.err.println("unsupported type: " + type);
			return null;
		}else{
			System.err.printf("unkonw type: %s(%s)\n", type, type.getClass());
			return null;
		}
	}

	private Hashtable<Method, HDLValue> methodIdTable = new Hashtable<Method, HDLValue>();
	
	@Override
	public void visitModule(Module o) {
		for(VariableDecl v: o.getVariables()){
			v.accept(this);
		}
		HDLUserDefinedType type = module.newUserDefinedType("methodId", new String[]{"IDLE"}, 0);		
		for(Method m: o.getMethods()){
			if(m.isConstructor()) continue;
			HDLValue v = type.addItem(m.getUniqueName());
			methodIdTable.put(m, v);
		}
		module.newSignal("methodId", type);
		for(Method m: o.getMethods()){
			m.accept(this);
		}
	}

	@Override
	public void visitBlockStatement(BlockStatement o) {
		for(Statement s: o.getStatements()){
			s.accept(this);
		}
	}

	@Override
	public void visitBreakStatement(BreakStatement o) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitContinueStatement(ContinueStatement o) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitExprStatement(ExprStatement o) {
		SynthesijerUtils.dump(o.getExpr());
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitForStatement(ForStatement o) {
		for(Statement s: o.getInitializations()){
			s.accept(this);
		}
		o.getBody().accept(this);
		for(Statement s: o.getUpdates()){
			s.accept(this);
		}
	}

	@Override
	public void visitIfStatement(IfStatement o) {
		o.getThenPart().accept(this);
		if(o.getElsePart() != null) o.getElsePart().accept(this);
	}

	@Override
	public void visitReturnStatement(ReturnStatement o) {
		if(o.getExpr() != null){
			HDLPort p = methodReturnTable.get(o.getScope().getMethod());
			HDLSequencer.SequencerState state = stateTable.get(o.getState());
			GenerateHDLExprVisitor v = new GenerateHDLExprVisitor(this);
			o.getExpr().accept(v);
			p.getSignal().setAssign(state, v.getResult());
		}
	}

	@Override
	public void visitSkipStatement(SkipStatement o) {
		// nothing to generate
	}

	@Override
	public void visitSwitchStatement(SwitchStatement o) {
		// TODO Auto-generated method stub
	}

	@Override
	public void visitSwitchCaseElement(Elem o) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitSynchronizedBlock(SynchronizedBlock o) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitTryStatement(TryStatement o) {
		o.getBody().accept(this);
	}

	@Override
	public void visitVariableDecl(VariableDecl o) {
		Variable var = o.getVariable();
		HDLType t = getHDLType(var.getType());
		if(t == null) return;
		HDLSignal s = module.newSignal(var.getUniqueName(), t);
		if(o.getInitExpr() != null){
			GenerateHDLExprVisitor v = new GenerateHDLExprVisitor(this);
			o.getInitExpr().accept(v);
			s.setResetValue(v.getResult());
		}
	}

	@Override
	public void visitWhileStatement(WhileStatement o) {
		o.getBody().accept(this);
	}

	@Override
	public void visitArrayType(ArrayType o) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitComponentType(ComponentType o) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitMySelfType(MySelfType o) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitPrimitivyTypeKind(PrimitiveTypeKind o) {
		// TODO Auto-generated method stub
		
	}

}

class Statemachine2HDLSequencerVisitor implements StatemachineVisitor {
	
	private final GenerateHDLModuleVisitor parent;
	private final HDLPort req;
	private final HDLPort busy;
	
	public Statemachine2HDLSequencerVisitor(GenerateHDLModuleVisitor parent, HDLPort req, HDLPort busy) {
		this.parent = parent;
		this.req = req;
		this.busy = busy;
	}

	@Override
	public void visitStatemachine(Statemachine o) {
		HDLSequencer hs = parent.module.newSequencer(o.getKey());
		for(State s: o.getStates()){
			parent.stateTable.put(s, hs.addSequencerState(s.getId()));
		}
		for(State s: o.getStates()){
			HDLSequencer.SequencerState ss = parent.stateTable.get(s);
			for(Transition c: s.getTransitions()){
				ss.addStateTransit(parent.stateTable.get(c.getDestination()));
			}
			if(s.isTerminate()){
				ss.addStateTransit(hs.getIdleState());
			}
		}
		HDLExpr kickExpr = parent.module.newExpr(HDLOp.EQ, req.getSignal(), HDLConstant.HIGH);
		HDLSequencer.SequencerState entryState = parent.stateTable.get(o.getEntryState()); 
		hs.getIdleState().addStateTransit(kickExpr, entryState);
		busy.getSignal().setAssign(null,
				parent.module.newExpr(HDLOp.IF,
						parent.module.newExpr(HDLOp.EQ, hs.getStateKey(), hs.getIdleState().getStateId()),
						HDLConstant.LOW,
						HDLConstant.HIGH));
	}
	
	@Override
	public void visitState(State o) {
		// TODO Auto-generated method stub
		
	}

}

class GenerateHDLExprVisitor implements SynthesijerExprVisitor{
	
	private final GenerateHDLModuleVisitor parent;
	
	private HDLExpr result;
	
	public GenerateHDLExprVisitor(GenerateHDLModuleVisitor parent){
		this.parent = parent;
	}
	
	public HDLExpr getResult(){
		return result;
	}

	@Override
	public void visitArrayAccess(ArrayAccess o) {
		if(o.getIndexed() instanceof Ident){
			String rdata = ((Ident)o.getIndexed()).getSymbol() + "_rdata";
			result = parent.module.newSignal(rdata, HDLPrimitiveType.genVectorType(32));
		}else{
			throw new RuntimeException(String.format("%s(%s) cannot convert to HDL.", o.getIndexed(), o.getIndexed().getClass()));
		}
	}

	@Override
	public void visitAssignExpr(AssignExpr o) {
		GenerateHDLExprVisitor v = new GenerateHDLExprVisitor(parent);
		o.getLhs().accept(v);
		result = v.getResult();
	}

	@Override
	public void visitAssignOp(AssignOp o) {
		GenerateHDLExprVisitor v = new GenerateHDLExprVisitor(parent);
		o.getLhs().accept(v);
		result = v.getResult();
	}

	@Override
	public void visitBinaryExpr(BinaryExpr o) {
		result = parent.module.newSignal("binaryexpr_result_" + this.hashCode(), HDLPrimitiveType.genVectorType(32));
	}

	@Override
	public void visitFieldAccess(FieldAccess o) {
		result = parent.module.newSignal(String.format("%s_%s", o.getSelected(), o.getIdent()), HDLPrimitiveType.genVectorType(32));
	}

	@Override
	public void visitIdent(Ident o) {
		result = parent.module.newSignal(o.getSymbol(), HDLPrimitiveType.genVectorType(32));
	}

	@Override
	public void visitLitral(Literal o) {
		HDLPrimitiveType t = HDLPrimitiveType.genUnkonwType();
		result = new HDLValue(o.getValueAsStr(), t);
	}

	@Override
	public void visitMethodInvocation(MethodInvocation o) {
		result = parent.module.newSignal(o.getMethodName() + "_return_value", HDLPrimitiveType.genVectorType(32));
	}

	@Override
	public void visitNewArray(NewArray o) {
		// TODO Auto-generated method stub		
	}

	@Override
	public void visitNewClassExpr(NewClassExpr o) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitParenExpr(ParenExpr o) {
		GenerateHDLExprVisitor v = new GenerateHDLExprVisitor(parent);
		o.getExpr().accept(v);
		result = v.getResult();
	}

	@Override
	public void visitTypeCast(TypeCast o) {
		GenerateHDLExprVisitor v = new GenerateHDLExprVisitor(parent);
		o.getExpr().accept(v);
		result = v.getResult();
	}

	@Override
	public void visitUnaryExpr(UnaryExpr o) {
		result = parent.module.newSignal("binaryexpr_result_" + this.hashCode(), HDLPrimitiveType.genVectorType(32));
	}
	
}