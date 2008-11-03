/*
 * See LICENSE file in distribution for copyright and licensing information.
 */
package ioke.lang.parser;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import ioke.lang.IokeObject;
import ioke.lang.Message;
import ioke.lang.Runtime;
import ioke.lang.Dict;
import ioke.lang.Number;
import ioke.lang.Symbol;

/**
 * Based on Levels from Io IoMessage_opShuffle.c
 * 
 * @author <a href="mailto:ola.bini@gmail.com">Ola Bini</a>
 */
public class Levels {
    public final static int OP_LEVEL_MAX = 32;
    private Runtime runtime;

    private Map<Object, Object> operatorTable;
    private Map<Object, Object> assignOperatorTable;

    public static class Level {
        IokeObject message;
        public static enum Type {Attach, Arg, New, Unused};
        Type type;
        int precedence;
        public Level(Type type) { this.type = type; }

        public void attach(IokeObject msg) {
            switch(type) {
            case Attach:
                Message.setNext(message, msg);
                break;
            case Arg:
                Message.addArg(message, msg);
                break;
            case New:
                message = msg;
                break;
            case Unused:
                break;
            }
        }

        public void setAwaitingFirstArg(IokeObject msg, int precedence) {
            this.type = Type.Arg;
            this.message = msg;
            this.precedence = precedence;
        }

        public void setAlreadyHasArgs(IokeObject msg) {
            this.type = Type.Attach;
            this.message = msg;
        }

        public void finish(List<IokeObject> expressions) {
            if(message != null) {
                Message.setNext(message, null);
                if(message.getArgumentCount() == 1) {
                    Object arg1 = message.getArguments().get(0);
                    if(arg1 instanceof IokeObject) { 
                        IokeObject arg = IokeObject.as(arg1);
                        if(arg.getName().length() == 0 && arg.getArgumentCount() == 1 && Message.next(arg) == null) {
                            int index = expressions.indexOf(arg);

                            if(index != -1) {
                                expressions.set(index, message);
                            } 
                           
                            message.getArguments().clear();
                            message.getArguments().addAll(arg.getArguments());
                            arg.getArguments().clear();


                        }
                    }
                }
            }
            type = Type.Unused;
        }
    }

    private List<Level> stack;

    private IokeObject _message;
    private IokeObject _context;

    private int currentLevel;
    private Level[] pool = new Level[OP_LEVEL_MAX];

    public static class OpTable {
        public String name;
        public int precedence;
        public OpTable(String name, int precedence) { this.name = name; this.precedence = precedence; }
    }

    public static OpTable[] defaultOperators = new OpTable[]{
		new OpTable("!",   0),
		new OpTable("'",   0),
		new OpTable("$",   0),
		new OpTable("~",   0),
		new OpTable("#",   0),

		new OpTable("**",  1),

		new OpTable("*",   2),
		new OpTable("/",   2),
		new OpTable("%",   2),

		new OpTable("+",   3),
		new OpTable("-",   3),

		new OpTable("<<",  4),
		new OpTable(">>",  4),

		new OpTable("<=>",  5),
		new OpTable(">",   5),
		new OpTable("<",   5),
		new OpTable("<=",  5),
		new OpTable(">=",  5),
		new OpTable("<>",  5),
		new OpTable("<>>",  5),

		new OpTable("==",  6),
		new OpTable("!=",  6),
		new OpTable("===",  6),
		new OpTable("=~",  6),
		new OpTable("!~",  6),

		new OpTable("&",   7),

		new OpTable("^",   8),

		new OpTable("|",   9),

		new OpTable("&&",  10),

		new OpTable("||",  11),

		new OpTable("..",  12),
		new OpTable("...",  12),
		new OpTable("=>",  12),
		new OpTable("<->",  12),
		new OpTable("->",  12),
		new OpTable("+>",  12),
		new OpTable("!>",  12),
		new OpTable("&>",  12),
		new OpTable("%>",  12),
		new OpTable("#>",  12),
		new OpTable("@>",  12),
		new OpTable("/>",  12),
		new OpTable("*>",  12),
		new OpTable("?>",  12),
		new OpTable("|>",  12),
		new OpTable("^>",  12),
		new OpTable("~>",  12),
		new OpTable("->>",  12),
		new OpTable("+>>",  12),
		new OpTable("!>>",  12),
		new OpTable("&>>",  12),
		new OpTable("%>>",  12),
		new OpTable("#>>",  12),
		new OpTable("@>>",  12),
		new OpTable("/>>",  12),
		new OpTable("*>>",  12),
		new OpTable("?>>",  12),
		new OpTable("|>>",  12),
		new OpTable("^>>",  12),
		new OpTable("~>>",  12),
		new OpTable("=>>",  12),
		new OpTable("**>",  12),
		new OpTable("**>>",  12),
		new OpTable("&&>",  12),
		new OpTable("&&>>",  12),
		new OpTable("||>",  12),
		new OpTable("||>>",  12),
		new OpTable("$>",  12),
		new OpTable("$>>",  12),

		new OpTable("+=",  13),
		new OpTable("-=",  13),
		new OpTable("*=",  13),
		new OpTable("/=",  13),
		new OpTable("%=",  13),
		new OpTable("and",  13),
		new OpTable("&=",  13),
		new OpTable("&&=",  13),
		new OpTable("^=",  13),
		new OpTable("or",  13),
		new OpTable("|=",  13),
		new OpTable("||=",  13),
		new OpTable("<<=", 13),
		new OpTable(">>=", 13),

		new OpTable("return", 14)
    };

    public static OpTable[] defaultAssignOperators = new OpTable[]{
		new OpTable("=", 2),
		new OpTable("+=", 2),
		new OpTable("-=", 2),
		new OpTable("/=", 2),
		new OpTable("*=", 2),
		new OpTable("%=", 2),
		new OpTable("&=", 2),
		new OpTable("&&=", 2),
		new OpTable("|=", 2),
		new OpTable("||=", 2),
		new OpTable("^=", 2),
		new OpTable("<<=", 2),
		new OpTable(">>=", 2),
		new OpTable("++", 1),
		new OpTable("--", 1)
    };
    
    public static interface OpTableCreator {
        Map<Object, Object> create(Runtime runtime);
    }

    public Levels(IokeObject msg, IokeObject context, IokeObject message) {
        this.runtime = context.runtime;
        this._context = context;
        this._message = message;

        IokeObject opTable = IokeObject.as(msg.findCell(_message, _context, "OperatorTable"));
        if(opTable == runtime.nul) {
            opTable = runtime.newFromOrigin();
            runtime.message.setCell("OperatorTable", opTable);
            opTable.setCell("precedenceLevelCount", runtime.newNumber(OP_LEVEL_MAX));
        }
        this.operatorTable = getOpTable(opTable, "operators", new OpTableCreator() {
                public Map<Object, Object> create(Runtime runtime) {
                    Map<Object, Object> table = new HashMap<Object, Object>();
                    for(OpTable ot : defaultOperators) {
                        table.put(runtime.getSymbol(ot.name), runtime.newNumber(ot.precedence));
                    }
                    return table;
                }
            });
        this.assignOperatorTable = getOpTable(opTable, "assignOperators", new OpTableCreator() {
                public Map<Object, Object> create(Runtime runtime) {
                    Map<Object, Object> table = new HashMap<Object, Object>();
                    for(OpTable ot : defaultAssignOperators) {
                        table.put(runtime.getSymbol(ot.name), runtime.newNumber(ot.precedence));
                    }
                    return table;
                }
            });
        this.stack = new ArrayList<Level>();
        this.reset();
    }



    public Map<Object, Object> getOpTable(IokeObject opTable, String name, OpTableCreator creator) {
        IokeObject operators = IokeObject.as(opTable.findCell(_message, _context, name));
        if(operators != runtime.nul && (IokeObject.data(operators) instanceof Dict)) {
            return Dict.getMap(operators);
        } else {
            Map<Object, Object> result = creator.create(runtime);
            opTable.setCell(name, runtime.newDict(result));
            return result;
        }
    }

    public int levelForOp(String messageName, IokeObject messageSymbol, IokeObject msg) {
        Object value = operatorTable.get(messageSymbol);
        if(value == null) {
            return -1;
        }

        return Number.value(value).intValue();
    }

    public void popDownTo(int targetLevel, List<IokeObject> expressions) {
        Level level = null;
        while((level = stack.get(0)) != null && level.precedence <= targetLevel && level.type != Level.Type.Arg) {
            stack.remove(0).finish(expressions);
            currentLevel--;
        }
    }

    public Level currentLevel() {
        return stack.get(0);
    }

    public boolean isAssignOperator(IokeObject messageSymbol) {
        return assignOperatorTable.containsKey(messageSymbol);
    }

    public int argsForAssignOperator(IokeObject messageSymbol) {
        Object value = assignOperatorTable.get(messageSymbol);
        if(value == null) {
            return 2;
        }

        return Number.value(value).intValue();
    }

    public String nameForAssignOperator(IokeObject messageSymbol) {
        return Symbol.getText(assignOperatorTable.get(messageSymbol));
    }
    
    public void attachAndReplace(Level self, IokeObject msg) {
        self.attach(msg);
        self.type = Level.Type.Attach;
        self.message = msg;
    }

    public void attachToTopAndPush(IokeObject msg, int precedence) {
        Level top = stack.get(0);
        attachAndReplace(top, msg);

        Level level = pool[currentLevel++];
        level.setAwaitingFirstArg(msg, precedence);
        stack.add(0, level);
    }
    
    public void attach(IokeObject msg, List<IokeObject> expressions) {
        // TODO: fix all places with setNext to do setPrev too!!!

        String messageName = Message.name(msg);
        IokeObject messageSymbol = runtime.getSymbol(messageName);
        int precedence = levelForOp(messageName, messageSymbol, msg);
        
        int msgArgCount = msg.getArgumentCount();
        
        /*
        // o a = b c . d  becomes  o =(a, b c) . d
        //
        // a      attaching
        // =      msg
        // b c    Message.next(msg)
        */
        if(isAssignOperator(messageSymbol) && msgArgCount == 0 && !((Message.next(msg) != null) && Message.name(Message.next(msg)).equals("="))) {
            Level currentLevel = currentLevel();
            IokeObject attaching = currentLevel.message;
            String setCellName;

            if(attaching == null) { // = b . 
                throw new RuntimeException("Can't create assignment expression without lvalue");
            }

			// a = b .
            String cellName = attaching.getName();
            IokeObject copyOfMessage = Message.copy(attaching);

            Message.setPrev(copyOfMessage, null);
            Message.setNext(copyOfMessage, null);

            attaching.getArguments().clear();
			// a = b .  ->  a(a) = b .
            Message.addArg(attaching, copyOfMessage);
            
            setCellName = messageName;
            int expectedArgs = argsForAssignOperator(messageSymbol);

            // a(a) = b .  ->  =(a) = b .
            Message.setName(attaching, setCellName);

            currentLevel.type = Level.Type.Attach;

            // =(a) = b .
			// =(a) = or =("a") = .
            IokeObject mn = Message.next(msg);
            
            if(expectedArgs > 1 && (mn == null || Message.isTerminator(mn))) { 
                // TODO: error, "compile error: %s must be followed by a value.", messageName
            }

            if(expectedArgs > 1) { 
                // =(a) = b c .  ->  =(a, b c .) = b c .
                Message.addArg(attaching, mn);

                // process the value (b c d) later  (=(a, b c d) = b c d .)
                if(Message.next(msg) != null && !Message.isTerminator(Message.next(msg))) {
                    expressions.add(0, Message.next(msg));
                }

                IokeObject last = msg;
                while(Message.next(last) != null && !Message.isTerminator(Message.next(last))) {
                    last = Message.next(last);
                }

                Message.setNext(attaching, Message.next(last));
                Message.setNext(msg, Message.next(last));
            
                if(last != msg) {
                    Message.setNext(last, null);
                }
            } else {
                Message.setNext(attaching, Message.next(msg));
            }
        } else if(Message.isTerminator(msg)) {
            popDownTo(OP_LEVEL_MAX-1, expressions);
            attachAndReplace(currentLevel(), msg);
        } else if(precedence != -1) { // An operator
            if(msgArgCount > 0) {
                // move arguments off to their own message to make () after operators behave like Cs grouping ()
                IokeObject brackets = runtime.newMessage("");
                Message.copySourceLocation(msg, brackets);
                brackets.getArguments().addAll(msg.getArguments());
                msg.getArguments().clear();

                // Insert the brackets message between msg and its next message
                Message.setNext(brackets, Message.next(msg));
                Message.setNext(msg, brackets);
            }
            popDownTo(precedence, expressions);
            attachToTopAndPush(msg, precedence);
        } else {
            attachAndReplace(currentLevel(), msg);
        }
    }

    public void nextMessage(List<IokeObject> expressions) {
        while(stack.size() > 0) {
            stack.remove(0).finish(expressions);
        }
        reset();
    }

    public void reset() {
        currentLevel = 1;
        for(int i=0;i<OP_LEVEL_MAX;i++) {
            pool[i] = new Level(Level.Type.Unused);
        }
        Level level = pool[0];
        level.message = null;
        level.type = Level.Type.New;
        level.precedence = OP_LEVEL_MAX;

        stack.clear();
        stack.add(0, pool[0]);
    }
}// Levels