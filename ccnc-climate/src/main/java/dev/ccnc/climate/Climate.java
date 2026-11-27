package dev.ccnc.climate;

/**
 * Climate state plus the source that feeds it. {@link DemoSource} simulates the
 * car off-unit; {@link DatcSource} talks to the HKMC DATC service on the bench unit.
 */
final class Climate {

    /** All temperatures are Celsius in the model; the UI converts for display. */
    /** Right-hand drive: the driver's temperature and seat controls on the right (see dev.ccnc.common.Drive). */
    static boolean rhd(android.content.Context c) { return dev.ccnc.common.Drive.rhd(c); }

    /**
     * Keeps what the driver just tapped on screen until the car reports it. The car confirms a seat or climate change
     * 0.4-0.8 s later, and a reading taken in between still carries the old value: shown as it is, the control would
     * jump back and then forward again, which feels slow. A reading is held back while it differs from the tap, for
     * at most HOLD_MS (then the car's value wins, e.g. when it refused the change).
     */
    static final class Hold {
        static final long HOLD_MS = 2500;
        private State target;
        private long until;

        void tapped(State s) { target = s.copy(); until = android.os.SystemClock.uptimeMillis() + HOLD_MS; }

        /** True when this car reading should replace the screen now. */
        boolean accept(State reading) {
            if (target == null) return true;
            if (reading.sameControls(target) || android.os.SystemClock.uptimeMillis() >= until) { target = null; return true; }
            return false;
        }
    }

    static final class State implements Cloneable {
        /** The settings a tap can change: equal means the car now shows what the driver chose. */
        boolean sameControls(State o) {
            return o != null && Math.abs(driverTemp - o.driverTemp) < 0.01f && Math.abs(passengerTemp - o.passengerTemp) < 0.01f
                    && dual == o.dual && power == o.power && auto == o.auto && blower == o.blower && mode == o.mode
                    && ac == o.ac && recirculated == o.recirculated && frontDefrost == o.frontDefrost && rearDefrost == o.rearDefrost
                    && driverSeatHeat == o.driverSeatHeat && passengerSeatHeat == o.passengerSeatHeat
                    && driverSeatVent == o.driverSeatVent && passengerSeatVent == o.passengerSeatVent
                    && rearLeftHeat == o.rearLeftHeat && rearLeftVent == o.rearLeftVent
                    && rearRightHeat == o.rearRightHeat && rearRightVent == o.rearRightVent && wheelHeat == o.wheelHeat;
        }

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
         * More of what {@link Equipment} decides from, -1 / null = not read: the wheel-heat option and switch info
         * (USM), the stock front-seat property persist.sys.showSWRCFrontSeat, DATC getClimateType() (1 / 5 single
         * zone, 2 dual, 4 with rear climate), isFrontMTCType() (1 = manual climate), CLIMATE_MODE_TYPE (0 = 4
         * air-flow modes, 1 = 6, 2 = 7) and CLIMATE_CAN_SUPPORT (0xe0 once the DATC variant is known).
         */
        int swOpt = -1, swInf = -1, climateType = -1, frontMtc = -1, modeType = -1, canSupport = -1;
        String seatProp;
        /** True once the equipment values above have been read (or set up by the demo): only then are they logged. */
        boolean equipRead;

        /**
         * Seat options as the stock app reads them (USM *_Option; SeatHeatVentPopup): 1/2 = heat, 3/4 = vent,
         * 5/6 = both, odd = 2 levels; this car reports front 6/6 (heat and vent) and rear 1/1 (heat only).
         */
        static boolean rearExists(int opt) { return opt >= 1 && opt <= 6; }
        static boolean rearHeat(int opt) { return opt == 1 || opt == 2 || opt == 5 || opt == 6; }
        static boolean rearVent(int opt) { return opt >= 3 && opt <= 6; }
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
        { state.drvOpt = 6; state.astOpt = 6; state.rlOpt = 1; state.rrOpt = 1; state.wheelAvail = true; // like the car
          state.swOpt = 1; state.swInf = 1; state.equipRead = true; }
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
