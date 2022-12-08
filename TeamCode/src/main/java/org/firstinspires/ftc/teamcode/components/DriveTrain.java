package org.firstinspires.ftc.teamcode.components;

import static org.firstinspires.ftc.teamcode.components.DriveTrain.Direction.X;
import static org.firstinspires.ftc.teamcode.components.DriveTrain.Direction.Y;
import static org.firstinspires.ftc.teamcode.util.DistanceUtil.inches;
import static org.firstinspires.ftc.teamcode.util.DistanceUtil.tilesToTicks;

import android.annotation.SuppressLint;

import com.qualcomm.hardware.bosch.BNO055IMU;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder;
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference;
import org.firstinspires.ftc.robotcore.external.navigation.Orientation;
import org.firstinspires.ftc.teamcode.util.Heading;
import org.firstinspires.ftc.teamcode.util.MecanumUtil;
import org.firstinspires.ftc.teamcode.util.Position;
import org.firstinspires.ftc.teamcode.util.Vector2;

import java.util.Arrays;
import java.util.List;

@SuppressLint("DefaultLocale")
public class DriveTrain extends BaseComponent {


    /**
     * The software of the drivetrain
     */
    private TileEdgeDetector tileEdgeDetectorSide;

    /**
     * The hardware for the drive train
     */
    private DcMotorEx frontLeft;
    private DcMotorEx frontRight;
    private DcMotorEx backLeft;
    private DcMotorEx backRight;
    private List<DcMotorEx> motors;
    private BNO055IMU imu;

    /**
     * The current position for the robot.
     */
    private Position position;

    /**
     * The current heading for the robot.
     */
    private Heading heading;

    /**
     * The current velocity vector in tiles / sec.
     */
    private Vector2 velocity;

    /**
     * The last orientation data obtained from the IMU.
     */
    private Orientation previousImuOrientation;

    /**
     * The last known ticks for the motors.
     */
    private MotorTicks previousMotorTicks;


    public DriveTrain(OpMode opMode, WebCam webCamSide) {
        super(opMode);

        frontLeft = (DcMotorEx) hardwareMap.dcMotor.get("FrontLeft");
        frontRight = (DcMotorEx) hardwareMap.dcMotor.get("FrontRight");
        backLeft = (DcMotorEx) hardwareMap.dcMotor.get("BackLeft");
        backRight = (DcMotorEx) hardwareMap.dcMotor.get("BackRight");
        motors = Arrays.asList(frontLeft, frontRight, backLeft, backRight);

        imu = hardwareMap.get(BNO055IMU.class, "imu");

        tileEdgeDetectorSide = new TileEdgeDetector(opMode, webCamSide);
        addSubComponents(tileEdgeDetectorSide);

        //todo: Decide how we are going to determine starting position

        // For now starting position is to be assumed the origin (0, 0)
        position = new Position(.5, .5);
        heading = new Heading(90);
        velocity = new Vector2(0, 0);
    }

    @Override
    public void init() {
        super.init();

        initIMU();

        this.frontLeft.setDirection(DcMotorSimple.Direction.REVERSE);
        this.frontRight.setDirection(DcMotorSimple.Direction.FORWARD);
        this.backLeft.setDirection(DcMotorSimple.Direction.REVERSE);
        this.backRight.setDirection(DcMotorSimple.Direction.FORWARD);

        for (DcMotorEx motor : motors) {
            motor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        }

        // Activate the side tile edge detector immediately
        tileEdgeDetectorSide.activate();
    }

    private void initIMU() {
        BNO055IMU.Parameters parameters = new BNO055IMU.Parameters();
        parameters.mode = BNO055IMU.SensorMode.IMU;
        parameters.angleUnit = BNO055IMU.AngleUnit.DEGREES;
        parameters.accelUnit = BNO055IMU.AccelUnit.METERS_PERSEC_PERSEC;
        parameters.loggingEnabled = false;

        imu.initialize(parameters);

        telemetry.addData("IMU", "calibrating...");
        telemetry.update();

        // make sure the imu gyro is calibrated before continuing.
        while (!isStopRequested() && !imu.isGyroCalibrated()) {
            sleep(50);
        }

        telemetry.addData("IMU", "waiting for start...");
        telemetry.addData("IMU Calibration Status", imu.getCalibrationStatus().toString());
        telemetry.update();

        // Start integration background thread, so we can get updated position in a loop.
        //imu.startAccelerationIntegration(null, null, 5);
    }

    public BNO055IMU getImu() {
        return imu;
    }

    /**
     * Return the heading of the robot as an angle in degrees from (0 - 360).
     */
    public double getHeading() {
        return heading.getValue();
    }

    /**
     * Return the current position of the robot.
     */
    public Position getPosition() {
        return position;
    }

    @Override
    public void updateStatus() {
        // Update the current position and heading based off of sensory data
        updateCurrentPosition();
        updateCurrentHeading();
        updateCurrentVelocity();

        telemetry.addData("Heading", heading);
        telemetry.addData("Position", position);

        if (tileEdgeDetectorSide.isDetected()) {
            telemetry.addData("Angle to Tile", String.format("%.2f", tileEdgeDetectorSide.getAngleToTile()));
            // todo: convert these to tile units instead of inches
            telemetry.addData("Distance to Tile (horiz)", String.format("%.2f in", tileEdgeDetectorSide.getDistanceToTileHorizontal() * 12.0));
            telemetry.addData("Distance to Tile (vert)", String.format("%.2f in", tileEdgeDetectorSide.getDistanceToTileVertical() * 12.0));
        }

        telemetry.addData("Current Command", getCurrentCommand());
        telemetry.addData("Next Commands", getNextCommands());

        // Now allow any commands to run with the updated data
        super.updateStatus();
    }

    /**
     * Updates the current position of the bot.
     */
    private void updateCurrentPosition() {

        // Determine the number of ticks moved by each wheel.
        MotorTicks ticks = getCurrentMotorTicks();

        telemetry.addData("Motor Ticks", ticks.toString());

        if (previousMotorTicks != null) {

            // If we have a previous tick count, calculate how far the robot has moved based on the delta in ticks,
            // and add that to the current position.

            int deltaBackLeft = ticks.backLeft - previousMotorTicks.backLeft;
            int deltaBackRight = ticks.backRight - previousMotorTicks.backRight;
            int deltaFrontLeft = ticks.frontLeft - previousMotorTicks.frontLeft;
            int deltaFrontRight = ticks.frontRight - previousMotorTicks.frontRight;

            Vector2 deltaPositionRelativeToField = MecanumUtil.calculatePositionOffsetFromWheelRotations(
                    deltaBackLeft,
                    deltaBackRight,
                    deltaFrontLeft,
                    deltaFrontRight,
                    heading
            );

            position = position.add(deltaPositionRelativeToField);
        }

        // Remember the current motor ticks for the next loop iteration
        previousMotorTicks = ticks;

        // todo: override this with a visual observation from hough code, if there is one

    }

    public void resetPosition() {
        position = new Position(0.5, 0.5);
    }

    private MotorTicks getCurrentMotorTicks() {
        return new MotorTicks(
                backLeft.getCurrentPosition(),
                backRight.getCurrentPosition(),
                frontLeft.getCurrentPosition(),
                frontRight.getCurrentPosition()
        );
    }

    /**
     * Updates the current heading of the bot.
     */
    private void updateCurrentHeading() {

        // The following code adapted with permission from team SkyStone 2019-2020.

        // We experimentally determined the Z axis is the axis we want to use for heading angle.
        // We have to process the angle because the imu works in euler angles so the Z axis is
        // returned as 0 to +180 or 0 to -180 rolling back to -179 or +179 when rotation passes
        // 180 degrees. We detect this transition and track the total cumulative angle of rotation.

        Orientation orientation = imu.getAngularOrientation(AxesReference.INTRINSIC, AxesOrder.ZYX, AngleUnit.DEGREES);

        if (previousImuOrientation == null) {
            previousImuOrientation = orientation;
        }

        double deltaAngle = orientation.firstAngle - previousImuOrientation.firstAngle;
        heading = heading.add(deltaAngle);

        previousImuOrientation = orientation;

        // todo: override this with a visual observation from hough code, if there is one
    }

    /**
     * Updates the current velocity of the bot.
     */
    private void updateCurrentVelocity() {

        // todo: implement this

    }

    /**
     * Sets the motor powers equal to the controllers inputs.
     */
    public void drive(double drive, double turn, double strafe) {
        drive(drive, turn, strafe, 1.0);
    }

    public void drive(double drive, double turn, double strafe, double speed) {

        // Stop any current command from executing.
        stopAllCommands();

        drive *= speed;
        turn *= speed;
        strafe *= speed;

        setMotorMode(DcMotorEx.RunMode.RUN_USING_ENCODER);

        frontLeft.setPower(drive - turn - strafe);
        frontRight.setPower(drive + turn + strafe);
        backRight.setPower(drive + turn - strafe);
        backLeft.setPower(drive - turn + strafe);
    }

    public void attemptDriverRelative(double drive, double turn, double strafe, double speed, double driverHeading) {
        Vector2 joyStickPosition = new Vector2(strafe,drive);

        Heading joyStickHeading = joyStickPosition.toHeading();

        Heading directionRobotWillMove = joyStickHeading.add(heading);

        //The direction the robot wants to go relative to itself
        double angle = Math.toRadians(directionRobotWillMove.delta(joyStickHeading));

        Vector2 powerVector = new Vector2(
                Math.sin(angle + Math.PI / 4.0),  // FL, BR
                Math.sin(angle - Math.PI / 4.0)   // FR, BL
        );

        Vector2 finalPower = powerVector.multiply(speed);

        double powerFLBR = finalPower.getX();
        double powerFRBL = finalPower.getY();

        frontLeft.setPower(powerFLBR);
        backRight.setPower(powerFLBR);

        frontRight.setPower(powerFRBL);
        backLeft.setPower(powerFRBL);
    }

    public enum Direction {
        X,
        Y
    }

    /**
     * Moves the given distance in tiles, in the given direction, at the given speed.
     *
     * @param distance  Move that many tiles. Backwards and Right are negative Directions.
     * @param direction The direction you want to move in
     * @param speed     The speed you want to move at
     */
    public void moveAlignedToTileCenter(double distance, Direction direction, double speed) {
        // todo: if current command is "move to target position and rotation", then update that
        // todo: command with a new position and rotation.
        executeCommand(new MoveAlignedToTileCenter(direction, distance, speed));
    }

    /**
     * Moves forward the given distance.
     *
     * @param distance the distance to move in tiles
     * @param speed    a factor 0-1 that indicates how fast to move
     */
    public void moveForward(double distance, double speed) {
        executeCommand(new MoveForward(distance, speed));
    }

    /**
     * Moves forward the given distance
     *
     * @param distance the distance to move in tiles. Positive to the left, Negative to the right
     * @param speed    a factor 0-1 that indicates how fast to move
     */
    public void strafe(double distance, double speed) {
        executeCommand(new Strafe(distance, speed));
    }

    /**
     * Turns the given angle
     *
     * @param angle Positive is left, negative is right, turns the given angle in degrees
     * @param speed 0-1, how fast we move
     */
    public void rotate(double angle, double speed) {
        executeCommand(new Rotate(angle, speed));
    }

    /**
     * Aligns the robot to the given angle from the edge of the tile.
     *
     * @param targetAngle the desired angle from the tile edge, in degrees.
     * @param speed       the master speed at which we travel
     * @param time        the time the detector will wait in seconds
     */
    public void alignToTileAngle(double targetAngle, double speed, double time) {
        if (tileEdgeDetectorSide.waitForDetection(time)) {
            double initialAngle = tileEdgeDetectorSide.getAngleToTile();
            double angle = targetAngle - initialAngle;

            double maxRotationForAlignment = 45.0;
            if (Math.abs(angle) < maxRotationForAlignment) {
                rotate(angle, speed);
            }
        }
    }

    public void alignToTileAngle(double targetAngle, double speed) {
        alignToTileAngle(targetAngle, speed, 1);
    }

    /**
     * Strafes the robot so that the edge of the right wheel is the requested distance from the edge of the closest
     * tile to the right.
     *
     * @param targetDistance the desired distance from the edge of the tile, in tiles.
     * @param speed          the master speed at which we travel
     * @param time           the time the detector will wait in seconds
     */
    public void moveDistanceFromTileEdge(double targetDistance, double speed, double time) {
        if (tileEdgeDetectorSide.waitForDetection(time)) {
            double initialDistance = tileEdgeDetectorSide.getDistanceToTileHorizontal();
            double distance = targetDistance - initialDistance;

            // Sanity check - don't try to move more than 10 inches
            double maxDistance = inches(10);
            if (Math.abs(distance) < maxDistance) {
                strafe(distance, speed);
            }
        }
    }

    public void moveDistanceFromTileEdge(double targetDistance, double speed) {
        moveDistanceFromTileEdge(targetDistance, speed, 1);
    }

    /**
     * Turns off all the motors.
     */
    private void stopMotors() {
        // Shut off the motor power
        for (DcMotorEx motor : motors) {
            motor.setPower(0);
        }
    }

    /**
     * Turns on all the motors.
     *
     * @param speed how fast the motors turn
     */
    private void setMotorPower(double speed) {
        // Shut off the motor power
        for (DcMotorEx motor : motors) {
            motor.setPower(speed);
        }
    }

    /**
     * Set the run mode for all motors.
     */
    private void setMotorMode(DcMotor.RunMode mode) {
        for (DcMotorEx motor : motors) {
            motor.setMode(mode);
        }

        // Also, if the encoders are being reset, forget the previous motor ticks, in order to keep the robot from
        // thinking it has jumped through space and time when updating the position in the next loop.
        if (mode == DcMotor.RunMode.RUN_WITHOUT_ENCODER || mode == DcMotor.RunMode.STOP_AND_RESET_ENCODER) {
            previousMotorTicks = null;
        }
    }

    /**
     * Calculates the average position for each motor.
     */
    private int averageMotorPosition() {
        int sum = 0;
        for (DcMotorEx motor : motors) {
            sum += Math.abs(motor.getCurrentPosition());
        }
        return sum / motors.size();
    }

    /**
     * Calculates a smooth power curve between any two positions (in ticks, degrees, inches, etc),
     * based on the current position, the initial position, and the target position.
     *
     * @param current the current measured position
     * @param initial the initial position that was moved from
     * @param target  the target position being moved to
     * @param speed   the master speed, with range 0.0 - 1.0
     */
    private double getPowerCurveForPosition(double current, double initial, double target, double speed) {
        // Scale the position to between 0 - 1
        double xVal = scaleProgress(current, initial, target);

        double minPower = 0.15;

        double power;
        if (xVal < 0.25) {
            power = 1 / (1 + Math.pow(Math.E, -16 * (2 * xVal - 0.125)));

            // While accelerating, gradually increase the min power with time
            //minPower += time.seconds() / 4.0;

        } else {
            power = 1 / (1 + Math.pow(Math.E, 8 * (2 * xVal - 1.675)));
        }

        power *= speed;

        if (power < minPower) {
            power = minPower;
        }

        return power;
    }

    /**
     * Scales current progress from an initial value to a target value, returning as a fraction between 0.0 - 1.0.
     */
    private double scaleProgress(double current, double initial, double target) {
        if (target == initial) return 1.0;
        return (current - initial) / (target - initial);
    }

    private abstract class BaseCommand implements Command {

        @Override
        public void stop() {
            stopMotors();
        }

        public String toString() {
            return getClass().getSimpleName();
        }

    }

    /**
     * This command will move in a path along the center line of a tile row or column.
     * <p>
     * If the robot is not aligned to the tile's center, this will also attempt to correct that by moving to tile
     * center along the path of movement.
     * <p>
     * If the robot's heading is not aligned to a 90 degree tile boundary, this will also attempt to correct that
     * by making the smallest rotation possible.
     */
    private class MoveAlignedToTileCenter extends BaseCommand {

        /**
         * The direction in which the robot should move.
         */
        private Direction direction;

        /**
         * The distance, in tiles, that the robot should move.
         */
        private double distance;

        /**
         * The speed at which to move (0 - 1).
         */
        private double speed;

        /**
         * The position that the robot is trying to achieve.
         */
        private Position targetPosition;

        /**
         * The heading that the robot is trying to achieve.
         */
        private Heading targetHeading;

        /**
         * The starting position of the robot
         */
        private Position startingPosition;

        public MoveAlignedToTileCenter(Direction direction, double distance, double speed) {
            this.direction = direction;
            this.distance = distance;
            this.speed = speed;
        }

        @Override
        public void start() {

            // Calculate the new desired heading by rounding to the nearest tile edge.
            targetHeading = heading.alignToRightAngle();

            // Calculate the new target position, aligned to the tile middle.
            if (direction == X) {
                targetPosition = new Position(position.getX() + distance, position.getY());
            } else if (direction == Y) {
                targetPosition = new Position(position.getX(), position.getY() + distance);
            }
            targetPosition = targetPosition.alignToTileMiddle();

            startingPosition = position;

            setMotorMode(DcMotorEx.RunMode.RUN_USING_ENCODER);

        }

        @Override
        public boolean updateStatus() {

            MecanumUtil.MotorPowers powers = MecanumUtil.calculateWheelPowerForTargetPosition(
                    position, heading,
                    targetPosition, targetHeading,
                    speed
            );

            telemetry.addData("startingPosition", startingPosition);
            telemetry.addData("targetPosition", targetPosition);
            telemetry.addData("targetHeading", targetHeading);
            telemetry.addData("motorPowers", powers);

            frontLeft.setPower(powers.frontLeft);
            backRight.setPower(powers.backRight);
            frontRight.setPower(powers.frontRight);
            backLeft.setPower(powers.backLeft);

            double distanceMoved = position.distance(startingPosition);
            return distanceMoved >= startingPosition.distance(targetPosition);
        }
    }

    private class RotateAlignedToTile extends BaseCommand {

        // input of number of 90 degree turns, as an integer
        // if you say 3, you want to rotate 270 degrees in the CCW direction


        @Override
        public void start() {

        }

        @Override
        public boolean updateStatus() {
            return false;
        }
    }

    private class MoveForward extends BaseCommand {

        /**
         * The distance we want to move.
         */
        private double distance;

        /**
         * The speed at which to move (0 - 1).
         */
        private double speed;

        /**
         * The number of ticks we want to move.
         */
        private int ticks;

        ////////////////////////////////////////////////
        //Test Code
        private double progress;
        ////////////////////////////////////////////////

        public MoveForward(double distance, double speed) {
            this.distance = distance;
            this.speed = speed;
        }

        @Override
        public void start() {

            ////////////////////////////////////////////////
            //Test Code
            progress = 0;
            ////////////////////////////////////////////////

            // Figure out the distance in ticks
            ticks = tilesToTicks(distance);

            setMotorMode(DcMotorEx.RunMode.STOP_AND_RESET_ENCODER);

            for (DcMotorEx motor : motors) {
                motor.setTargetPosition(ticks);
            }

            setMotorMode(DcMotorEx.RunMode.RUN_TO_POSITION);
        }

        @Override
        public boolean updateStatus() {

            // Check if we've reached the correct number of ticks
            int ticksMoved = averageMotorPosition();

            telemetry.addData("ticks moved", ticksMoved);
            telemetry.addData("ticks", ticks);

            setMotorPower(getPowerCurveForPosition(ticksMoved, 0, Math.abs(ticks), speed));
            return progress >= 1.0;
        }
    }

    private class Strafe extends BaseCommand {

        /**
         * The distance we want to move. Negative direction is to the right, Positive to the left
         */
        private double distance;

        /**
         * The speed at which to move.
         */
        private double speed;

        private int ticks;

        ////////////////////////////////////////////////
        //Test Code
        private double progress;
        ////////////////////////////////////////////////

        // todo check distance multiplied by strafe modifier
        public Strafe(double distance, double speed) {
            this.distance = distance;
            this.speed = speed;
        }

        @Override
        public void start() {
            ////////////////////////////////////////////////
            //Test Code
            progress = 0;
            ////////////////////////////////////////////////

            ticks = tilesToTicks(distance);

            setMotorMode(DcMotorEx.RunMode.STOP_AND_RESET_ENCODER);

            frontLeft.setTargetPosition(-ticks);
            frontRight.setTargetPosition(ticks);
            backLeft.setTargetPosition(ticks);
            backRight.setTargetPosition(-ticks);

            setMotorMode(DcMotor.RunMode.RUN_TO_POSITION);
        }

        @Override
        public boolean updateStatus() {
            int ticksMoved = averageMotorPosition();

            telemetry.addData("tick moved", ticksMoved);
            telemetry.addData("ticks", ticks);

            setMotorPower(getPowerCurveForPosition(ticksMoved, 0, Math.abs(ticks), speed));

            return progress >= 1.0;
        }
    }

    private class Rotate extends BaseCommand {

        private Heading initialHeading;
        private Heading targetHeading;
        private double speed;

        public Rotate(double angle, double speed) {
            this.initialHeading = heading;
            this.targetHeading = heading.add(angle);
            this.speed = speed;
        }

        @Override
        public void start() {
            setMotorMode(DcMotorEx.RunMode.RUN_USING_ENCODER);
        }

        @Override
        public boolean updateStatus() {

            double power = getPowerCurveForPosition(heading.getValue(), initialHeading.getValue(), targetHeading.getValue(), speed);

            //if problems check this
            if (targetHeading.delta(heading) < 0) {
                power = -power;
            }
            double progress = scaleProgress(heading.getValue(), initialHeading.getValue(), targetHeading.getValue());

            telemetry.addData("Heading", heading);
            telemetry.addData("Initial Heading", initialHeading);
            telemetry.addData("Target Heading", targetHeading);
            telemetry.addData("Motor Power Curve", power);

            frontLeft.setPower(-power);
            backLeft.setPower(-power);
            frontRight.setPower(power);
            backRight.setPower(power);

            return progress >= 1.0;
        }

    }

    private static class MotorTicks {
        int backLeft;
        int backRight;
        int frontLeft;
        int frontRight;

        public MotorTicks(int backLeft, int backRight, int frontLeft, int frontRight) {
            this.backLeft = backLeft;
            this.backRight = backRight;
            this.frontLeft = frontLeft;
            this.frontRight = frontRight;
        }

        public String toString() {
            return String.format(
                    "BL %d\tBR %d\tFL %d\tFR%d",
                    backLeft, backRight, frontLeft, frontRight
            );
        }
    }

}
