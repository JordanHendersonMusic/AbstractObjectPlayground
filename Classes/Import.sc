Import : ExperimentalAbstractObject {
	var path, env;

	*new { |path|
		var env = Environment.make{ path.loadRelative };
		Import.respondingMethods.do { |m|
			if (env.includesKey(m.name)) {
				Error("Cannot have an environmental variable in an import with name '%' as it conflicts with Import".format(m.name)).throw
			}
		};
		^super.newCopyArgs(path: path, env: env);
	}

	asString { |limit=100|
		var string = String.streamContentsLimit({ |stream|
			env.printItemsOn(stream)
		}, limit);
		^"Import[" ++ string ++ "]"
	}

	postln { ^this.asString.postln }
	printOn { |stream| stream << this.asString }
	storeOn { |stream| this.asString.printOn(stream) }


	doesNotUnderstand { |selector ...a, k|
		var found;
		if (selector.isSetter) {
			env[selector.asGetter] = a[0];
			^this;
		} {
			found = env[selector];
			found !? { ^found.valueArgs(a, k) } ?? {
				Error("Import at '%' has no entry '%'".format(path, selector)).throw
			};
		}
	}
}