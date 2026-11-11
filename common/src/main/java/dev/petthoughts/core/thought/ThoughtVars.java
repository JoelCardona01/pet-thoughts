package dev.petthoughts.core.thought;

/**
 * Optional values a line can use: the name of the mob involved, of another pet, and the id of the
 * food item ("cooked_beef"). Null means "use the generic word from the language file".
 */
public record ThoughtVars(String mob, String friend, String food) {

    public static final ThoughtVars NONE = new ThoughtVars(null, null, null);

    public static ThoughtVars mob(String name) {
        return new ThoughtVars(name, null, null);
    }

    public static ThoughtVars friend(String name) {
        return new ThoughtVars(null, name, null);
    }

    public static ThoughtVars food(String itemId) {
        return new ThoughtVars(null, null, itemId);
    }
}
