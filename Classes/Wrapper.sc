Wrapper : AbstractObject {
	var held, beforeAction, afterAction;
	*new {|held, beforeAction, afterAction ... a, k|
		^super.superPerformArgs(\newCopyArgs, a, [held: held, beforeAction: beforeAction, afterAction: afterAction] ++ k);
	}
	doesNotUnderstand { |selector... a, k|
		var result;
		beforeAction !? { beforeAction.(held, selector, a, k) };
		protect {
			result = held.performArgs(selector, a, k);
		} { |error|
			afterAction !? { afterAction.(held, selector, a, k, result ?? { error }) }
		};
		^result;
	}
}

Debugger : Wrapper {
	var cond;

	*new { |held|
		var c = CondVar();
		var beforeAction = {
			|v, selector, args, kwargs|
			"About to call % with % and %".format(selector, args, kwargs).postln;
			c.wait;
		};
		var afterAction = {
			|v, selector, args, kwagrs, resultOrError|
			"result of %.% was %".format(v, selector, resultOrError).postln
		};
		^super.newCopyArgs(held: held, beforeAction: beforeAction, afterAction: afterAction, cond: c);
	}

	nextStep {
		cond.signalAll;
		^nil // return nil as print will block attempt wait on the main thread.
	}
}