package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;

/**
 * Base command that turns argument parsing into a declarative list of ArgSpecs.
 *
 * The resulting ScriptEntry contents are identical to what the old hand-written
 * parseArgs methods produced: same object names, same types, same required checks
 * and the same error message for missing arguments.
 */
public abstract class AbstractDbCommand extends AbstractCommand {

    private final ArgSpec[] specs;
    private final String requiredError;

    protected AbstractDbCommand(String name, String syntax, int required, int max,
                                String requiredError, ArgSpec... specs) {
        setName(name);
        setSyntax(syntax);
        setRequiredArguments(required, max);
        this.specs = specs;
        this.requiredError = requiredError;
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsException {
        for (Argument arg : scriptEntry) {
            ArgSpec spec = specFor(arg);
            if (spec == null || scriptEntry.hasObject(spec.name)) {
                arg.reportUnhandled();
                continue;
            }
            scriptEntry.addObject(spec.name, spec.parse(arg));
        }
        for (ArgSpec spec : specs) {
            if (spec.required && !scriptEntry.hasObject(spec.name)) {
                throw new InvalidArgumentsException(requiredError);
            }
        }
    }

    private ArgSpec specFor(Argument arg) {
        for (ArgSpec spec : specs) {
            if (spec.matches(arg)) {
                return spec;
            }
        }
        return null;
    }
}
