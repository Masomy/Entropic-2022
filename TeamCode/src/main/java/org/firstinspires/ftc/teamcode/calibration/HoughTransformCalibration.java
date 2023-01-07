package org.firstinspires.ftc.teamcode.calibration;

import static org.firstinspires.ftc.teamcode.Controller.AnalogControl.LEFT_STICK_Y;
import static org.firstinspires.ftc.teamcode.Controller.AnalogControl.LEFT_TRIGGER;
import static org.firstinspires.ftc.teamcode.Controller.AnalogControl.RIGHT_STICK_Y;
import static org.firstinspires.ftc.teamcode.Controller.AnalogControl.RIGHT_TRIGGER;
import static org.firstinspires.ftc.teamcode.Controller.Button.B;
import static org.firstinspires.ftc.teamcode.Controller.Button.DPAD_DOWN;
import static org.firstinspires.ftc.teamcode.Controller.Button.DPAD_LEFT;
import static org.firstinspires.ftc.teamcode.Controller.Button.DPAD_RIGHT;
import static org.firstinspires.ftc.teamcode.Controller.Button.DPAD_UP;

import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import org.firstinspires.ftc.teamcode.BaseTeleOp;
import org.firstinspires.ftc.teamcode.components.Robot;
import org.firstinspires.ftc.teamcode.components.TileEdgeDetector;
import org.firstinspires.ftc.teamcode.util.HoughLineDetector;

@TeleOp(group = "Calibration")
public class HoughTransformCalibration extends BaseTeleOp {

    @Override
    protected Robot.CameraMode getCameraMode() {
        return Robot.CameraMode.ENABLED_AND_STREAMING_SIDE;
    }

    @Override
    public void loop() {

        TileEdgeDetector tileEdgeDetector = robot.getDriveTrain().getTileEdgeDetectorSide();
        HoughLineDetector.HoughParameters parametersVertical = tileEdgeDetector.getHoughLineDetectorVertical().getParameters();
        HoughLineDetector.HoughParameters parametersHorizontal = tileEdgeDetector.getHoughLineDetectorHorizontal().getParameters();

        if (controller.isPressed(LEFT_STICK_Y)) {
            if (controller.isPressed(DPAD_UP)) {
                parametersVertical.similarLineRhoThreshold += .05;
            } else if (controller.isPressed(DPAD_DOWN)) {
                parametersVertical.similarLineRhoThreshold -= .05;
            } else if (controller.isPressed(DPAD_RIGHT)) {
                parametersVertical.similarLineThetaThreshold += 1;
            } else if (controller.isPressed(DPAD_LEFT)) {
                parametersVertical.similarLineThetaThreshold -= 1;
            }
        } else if (controller.isPressed(RIGHT_STICK_Y)) {
            if (controller.isPressed(DPAD_UP)) {
                parametersHorizontal.similarLineRhoThreshold += .05;
            } else if (controller.isPressed(DPAD_DOWN)) {
                parametersHorizontal.similarLineRhoThreshold -= .05;
            } else if (controller.isPressed(DPAD_RIGHT)) {
                parametersHorizontal.similarLineThetaThreshold += 1;
            } else if (controller.isPressed(DPAD_LEFT)) {
                parametersHorizontal.similarLineThetaThreshold -= 1;
            }
        } else if (controller.isPressed(LEFT_TRIGGER)) {
            // Adjust horizontal voter pixels
            if (controller.isPressed(DPAD_DOWN)) {
                parametersHorizontal.pixelVoterThreshold -= 5;
            } else if (controller.isPressed(DPAD_UP)) {
                parametersHorizontal.pixelVoterThreshold += 5;
            }

        } else if (controller.isPressed(RIGHT_TRIGGER)) {
            // Adjust vertical voter pixels
            if (controller.isPressed(DPAD_DOWN)) {
                parametersVertical.pixelVoterThreshold -= 5;
            } else if (controller.isPressed(DPAD_UP)) {
                parametersVertical.pixelVoterThreshold += 5;
            }

        } else {
            if (controller.isPressed(DPAD_DOWN)) {
                robot.getWebCamSide().setExposure(robot.getWebCamSide().getExposure() - 1);
            } else if (controller.isPressed(DPAD_UP)) {
                robot.getWebCamSide().setExposure(robot.getWebCamSide().getExposure() + 1);
            } else if (controller.isPressed(B)) {
                robot.getWebCamSide().saveLastFrame();
            }
        }

        telemetry.addData("Exposure (ms)", robot.getWebCamSide().getExposure());
        telemetry.addData("Horizontal Similar Line Rho Threshold", parametersHorizontal.similarLineRhoThreshold);
        telemetry.addData("Horizontal Similar Line Theta Threshold", parametersHorizontal.similarLineThetaThreshold);
        telemetry.addData("Vertical Similar Line Rho Threshold", parametersVertical.similarLineRhoThreshold);
        telemetry.addData("Vertical Similar Line Theta Threshold", parametersVertical.similarLineThetaThreshold);

        telemetry.addData("Horizontal Pixel Voters", tileEdgeDetector.getHoughLineDetectorHorizontal().getParameters().pixelVoterThreshold);
        telemetry.addData("Vertical Pixel Voters", tileEdgeDetector.getHoughLineDetectorVertical().getParameters().pixelVoterThreshold);

        robot.updateStatus();
    }
}
