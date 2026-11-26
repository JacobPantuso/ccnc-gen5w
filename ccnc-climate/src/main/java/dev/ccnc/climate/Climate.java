package dev.ccnc.climate;

/**
 * Climate state plus the source that feeds it. {@link DemoSource} simulates the
 * car off-unit; {@link DatcSource} talks to the HKMC DATC service on the bench unit.
 */
final class Climate {

    /** All temperatures are Celsius in the model; the UI converts for display. */
    /** Right-hand drive: the driver's temperature and seat controls on the right (see dev.ccnc.common.Drive). */
    static boolean rhd(android.content.Context c) { return dev.ccnc.common.Drive.rhd(c); }

    static final class State implements Cloneable {
        State copy() {
            try { return (State) clone(); } catch (CloneNotSupportedException e) { throw new AssertionError(e); }
        }
        float driverTemp = 21.5f;
        float passengerTemp = 21.5f;
        boolean dual;
        boolean power = true;
        boolean auto;
        int blower = 3; // 1..8 (0 = off when not AUTO)
        static final int MODE_FACE = 0, MODE_FACE_FEET = 1, MODE_FEET = 2, MODE_FEET_DEFOG = 3, MODE_DEFOG = 4;
        int mode = MODE_FACE;
        boolean ac = true;
        boolean recirculated;
        boolean frontDefrost;
        boolean rearDefrost;
        int driverSeatHeat;   // 0..3
        int passengerSeatHeat;
        int driverSeatVent;
        int passengerSeatVent;
        boolean celsius = true;
        /** True while the values come from the vehicle rather than the demo. */
        boolean fromVehicle;
        /** False when the car doesn't report the air-flow mode (the screen then shows no flow, and the mode popup is off). */
        boolean modeKnown = true;
        /** ccNC air-flow mode number read from the car (0 = not read). */
        int ccMode;
        /** USM seat/wheel (stock path): rear levels, wheel heat, raw option values, -1 = not read. */
        int rearLeftHeat, rearLeftVent, rearRightHeat, rearRightVent;
        boolean wheelAvail, wheelHeat;
        int drvOpt = -1, astOpt = -1, rlOpt = -1, rrOpt = -1;

        /**
         * Seat options as the stock ClimateManager reads them (USM *_Option): rear 1/2 = heat, 3/4 = vent, 5 = both;
         * this car reports front 6/6 (heat and vent) and rear 1/1 (heat only).
         */
        static boolean rearExists(int opt) { return opt >= 1 && opt <= 5; }
        static boolean rearHeat(int opt) { return opt == 1 || opt == 2 || opt == 5; }
        static boolean rearVent(int opt) { return opt == 3 || opt == 4 || opt == 5; }
        boolean anyRear() { return rearExists(rlOpt) || rearExists(rrOpt); }
    }

    interface Source {
        interface Listener { void onClimate(State s); }
        void start(Listener l);
        void stop();
        boolean isVehicle();
        void apply(State s);
    }

    /** Interactive simulation used off the car (and as fallback). */
    static final class DemoSource implements Source {
        final State state = new State();
        { state.drvOpt = 6; state.astOpt = 6; state.rlOpt = 1; state.rrOpt = 1; state.wheelAvail = true; } // like the car
        private Listener listener;

        @Override public void start(Listener l) {
            listener = l;
            emit();
        }

        @Override public void stop() { listener = null; }

        @Override public boolean isVehicle() { return false; }

        @Override public void apply(State s) {
            if (!s.power) { // power off clears the toggles, like the real panel
                s.auto = false;
                s.blower = 0;
                s.ac = false;
                s.frontDefrost = false;
                s.rearDefrost = false;
            } else if (s.auto && s.blower == 0) {
                s.blower = 3;
            }
            emit();
        }

        private void emit() {
            if (listener != null) listener.onClimate(state);
        }
    }
}
