package eu.lucaventuri.common;

import java.util.List;
import java.util.Vector;

/**
 * Class holding Multiple Exitable
 */
public class MultiExitable extends Exitable {
    private final List<Exitable> list = new Vector<>();

    public MultiExitable() {
    }

    public MultiExitable(boolean waitOnClose) {
        super(waitOnClose ? CloseStrategy.SEND_POISON_PILL_AND_WAIT : CloseStrategy.ASK_EXIT);
    }

    public MultiExitable(CloseStrategy closeStrategy) {
        super(closeStrategy);
    }

    public MultiExitable(Exitable... exitables) {
        for (Exitable exit : exitables)
            add(exit);
    }

    public MultiExitable(CloseStrategy closeStrategy, Exitable... exitables) {
        super(closeStrategy);

        for (Exitable exit : exitables)
            add(exit);
    }

    public MultiExitable(boolean waitOnClose, Exitable... exitables) {
        this(waitOnClose ? CloseStrategy.SEND_POISON_PILL_AND_WAIT : CloseStrategy.ASK_EXIT, exitables);
    }


    public <T extends Exitable> T add(T element) {
        if (isExiting()) {
            element.sendPoisonPill();
            element.askExit();
        }

        list.add(element);

        return element;
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
        if (list.size() == 0)
            return false;
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

    @Override
    public boolean sendPoisonPill() {
        for (Exitable elem : list)
            elem.sendPoisonPill();

        return true;
    }
}
