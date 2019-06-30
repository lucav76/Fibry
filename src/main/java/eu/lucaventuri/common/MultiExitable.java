package eu.lucaventuri.common;

import java.util.List;
import java.util.Vector;

/** Class holding Multiple Exitable */
public class MultiExitable extends Exitable {
    private final List<Exitable> list = new Vector<>();

    public void add(Exitable element) {
        list.add(element);

        if (isExiting() || isFinished())
            element.askExit();
    }

    public void remove(Exitable element) {
        list.remove(element);
    }

    @Override
    public boolean isExiting() {
        return super.isExiting();
    }

    @Override
    public boolean isFinished() {
        for (Exitable elem : list) {
            if (!elem.isFinished())
                return false;
        }

        return true;
    }

    @Override
    public void askExit() {
        super.askExit();

        for (Exitable elem : list)
            elem.askExit();
    }

    @Override
    public void waitForExit() {
        while (!isFinished()) {
            SystemUtils.sleep(1);
        }
    }

    public int size() {
        return list.size();
    }

    public Exitable evictRandomly(boolean askExit) {
        return Exceptions.silence(() -> {
            Exitable chosen = list.remove(0);

            if (askExit)
                chosen.askExit();

            return chosen;
        }, null);
    }
}
