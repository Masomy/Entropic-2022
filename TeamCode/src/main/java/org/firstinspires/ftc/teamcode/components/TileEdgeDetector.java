package org.firstinspires.ftc.teamcode.components;

import static org.firstinspires.ftc.teamcode.geometry.TileEdgeSolver.TileEdgeObservation;
import static org.firstinspires.ftc.teamcode.util.DistanceUtil.tilesToInches;
import static org.firstinspires.ftc.teamcode.util.FormatUtil.format;
import static org.firstinspires.ftc.teamcode.util.HoughLineDetector.HoughLine;
import static org.firstinspires.ftc.teamcode.util.HoughLineDetector.HoughParameters;

import android.annotation.SuppressLint;

import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.teamcode.RobotDescriptor;
import org.firstinspires.ftc.teamcode.components.WebCam.FrameContext;
import org.firstinspires.ftc.teamcode.geometry.Line;
import org.firstinspires.ftc.teamcode.geometry.Position;
import org.firstinspires.ftc.teamcode.geometry.TileEdgeSolver;
import org.firstinspires.ftc.teamcode.util.Color;
import org.firstinspires.ftc.teamcode.util.DrawUtil;
import org.firstinspires.ftc.teamcode.util.HoughLineDetector;
import org.opencv.core.Mat;
import org.opencv.core.Size;

import java.util.ArrayList;
import java.util.List;

public class TileEdgeDetector extends BaseComponent {

    /**
     * The maximum time in seconds that we will use a previous frame's detection before discarding it.  This can make
     * the detection more reliable in case we miss detection on a few frames for some reason.
     */
    private static final double PREVIOUS_DETECTION_THRESHOLD = 0.2;

    /**
     * The webcam to use for observations
     */
    private WebCam webCam;

    /**
     * Describes the location and physical characteristics of the webcam.
     */
    private RobotDescriptor.WebCamDescriptor webCamDescriptor;

    /**
     * The detector for finding horizontal lines in the webcam image.
     */
    private HoughLineDetector houghLineDetectorHorizontal;

    /**
     * The detector for finding vertical lines in the webcam image.
     */
    private HoughLineDetector houghLineDetectorVertical;

    /**
     * The solver that converts the tile edges found in the webcam image into an observation of the robot's position.
     */
    private TileEdgeSolver tileEdgeSolver;

    /**
     * The most recently acquired observation.
     */
    private TileEdgeObservation observation;

    /**
     * Aggregates observations from multiple webcams to provide the most current observation data.
     */
    private TileEdgeObservationAggregator aggregator;

    private FrameProcessor frameProcessor;

    public TileEdgeDetector(
            RobotContext context,
            WebCam webCam,
            TileEdgeObservationAggregator aggregator
    ) {
        super(context);
        this.webCam = webCam;
        this.webCamDescriptor = webCam.getWebCamDescriptor();
        this.aggregator = aggregator;
        reset();
    }

    @Override
    public void init() {
        Size resolution = webCam.getResolution();

        double verticalInches = Math.abs(webCamDescriptor.topLeft.robot.minus(
                webCamDescriptor.bottomLeft.robot).getY());
        double horizontalInches = Math.abs(webCamDescriptor.topRight.robot.minus(
                webCamDescriptor.topLeft.robot).getX());
        double verticalPixelsPerInch = resolution.height / verticalInches;
        double horizontalPixelsPerInch = resolution.width / horizontalInches;

        HoughParameters horizontalParameters = new HoughParameters();
        horizontalParameters.similarLineRhoThreshold = 1.2 * horizontalPixelsPerInch;
        horizontalParameters.similarLineThetaThreshold = 4.0;  // degrees
        horizontalParameters.minTheta = 45;
        horizontalParameters.maxTheta = 135;
        horizontalParameters.pixelVoterThreshold = 100; //(int) (resolution.width * (1.0 / 4.0));
        this.houghLineDetectorHorizontal = new HoughLineDetector(horizontalParameters);

        HoughParameters verticalParameters = new HoughParameters();
        verticalParameters.similarLineRhoThreshold = 1.2 * verticalPixelsPerInch;
        verticalParameters.similarLineThetaThreshold = 4.0;  // degrees
        verticalParameters.minTheta = -45;
        verticalParameters.maxTheta = 45;
        verticalParameters.pixelVoterThreshold = 90; //(int) (resolution.height * (1.0 / 4.0));
        this.houghLineDetectorVertical = new HoughLineDetector(verticalParameters);

        this.tileEdgeSolver = new TileEdgeSolver(context, webCamDescriptor);
    }

    public HoughLineDetector getHoughLineDetectorHorizontal() {
        return houghLineDetectorHorizontal;
    }

    public HoughLineDetector getHoughLineDetectorVertical() {
        return houghLineDetectorVertical;
    }

    public void activate() {
        frameProcessor = new FrameProcessor();
        webCam.setFrameProcessor(frameProcessor);
    }

    public boolean isActive() {
        return frameProcessor != null;
    }

    public void deactivate() {
        webCam.removeFrameProcessor();
        frameProcessor = null;
        reset();
    }

    /**
     * Indicates whether the tile edge is currently detected.
     */
    public boolean isDetected() {
        return observation != null;
    }

    public void reset() {
        aggregator.reset();
    }

    public TileEdgeObservation getObservation() {
        return observation;
    }

    private class FrameProcessor implements WebCam.FrameProcessor {

        @SuppressLint("DefaultLocale")
        @Override
        public void processFrame(Mat input, Mat output, FrameContext frameContext) {

            // Remember the time of the current frame capture as early as possible (before all the math).
            ElapsedTime beginFrameTime = new ElapsedTime();

            List<Line> lines = new ArrayList<>();
            for (HoughLine houghLine : houghLineDetectorHorizontal.detectLines(input)) {
                lines.add(houghLine.toLine(webCam.getResolution()));
            }
            for (HoughLine houghLine : houghLineDetectorVertical.detectLines(input)) {
                lines.add(houghLine.toLine(webCam.getResolution()));
            }

            TileEdgeObservation observation = tileEdgeSolver.solve(lines);

            if (observation != null) {
                // Remember the observation so that it can be used by the drivetrain.
                observation.setObservationTime(beginFrameTime);
                TileEdgeDetector.this.observation = observation;

                // If there is an aggregator, also add this observation to it.
                aggregator.add(observation);

            } else {
                // Keep the previous detection results if they are still within the previous detection threshold,
                // This helps in case we skip a frame or two for some reason.
                // Otherwise, discard the result so we no longer have a detection.
                TileEdgeObservation previousObservation = TileEdgeDetector.this.observation;
                if (previousObservation != null && previousObservation.observationTime.seconds() > PREVIOUS_DETECTION_THRESHOLD) {
                    reset();
                }
            }

            // Draw the observation details on the screen.
            if (webCam.isStreaming()) {
                drawOutput(output, observation);
            }
        }

        private void drawOutput(Mat output, TileEdgeObservation observation) {
            if (observation != null) {
                for (Line badLine : observation.badLines) {
                    DrawUtil.drawLine(output, badLine, Color.BLACK);
                }
                for (Line unusedLine : observation.unusedLines) {
                    DrawUtil.drawLine(output, unusedLine, Color.WHITE);
                }
                if (observation.observedFrontEdge != null) {
                    DrawUtil.drawLine(output, observation.observedFrontEdge, Color.BLUE);
                }
                if (observation.observedRightEdge != null) {
                    DrawUtil.drawLine(output, observation.observedRightEdge, Color.GREEN);
                }
            }

            TileEdgeObservation aggregate = aggregator.getAggregateObservation();
            if (aggregate != null) {
                String distanceRightInches = aggregate.distanceRight != null ?
                        format(tilesToInches(aggregate.distanceRight) -
                                robotDescriptor.robotDimensionsInInches.width / 2, 1) +
                                " in, [" + aggregator.countRight + " obs]" :
                        "___";
                String distanceFrontInches = aggregate.distanceFront != null ?
                        format(tilesToInches(aggregate.distanceFront) -
                                robotDescriptor.robotDimensionsInInches.height / 2, 1) +
                                " in, [" + aggregator.countFront + " obs]" :
                        "___";
                String headingOffset = aggregate.headingOffset != null ?
                        format(aggregate.headingOffset, 1) +
                                " deg, [" + aggregator.countHeading + "]" :
                        "___";

                DrawUtil.drawText(output, "DR " + distanceRightInches, new Position(50, 20), Color.ORANGE, 0.5, 1);
                DrawUtil.drawText(output, "DF " + distanceFrontInches, new Position(50, 50), Color.ORANGE, 0.5, 1);
                DrawUtil.drawText(output, "Heading " + headingOffset, new Position(50, 80), Color.ORANGE, 0.5, 1);
            }

            if (context.robotPositionProvider != null) {
                Position position = context.robotPositionProvider.getPosition();
                DrawUtil.drawText(output, "Position " + position.toString(2),
                        new Position(50, 140), Color.ORANGE, 0.5, 1);
            }
        }

    }

    public static class TileEdgeObservationAggregator {

        private TileEdgeObservation aggregate;
        private int countRight;
        private int countFront;
        private int countHeading;

        public synchronized void add(TileEdgeObservation observation) {
            if (observation == null) return;

            if (aggregate == null) {
                aggregate = new TileEdgeObservation();
            }

            aggregate.observationTime = observation.observationTime;

            if (observation.distanceRight != null) {
                countRight++;
                if (aggregate.distanceRight != null) {
                    aggregate.distanceRight = (observation.distanceRight + (aggregate.distanceRight * (countRight - 1))) / countRight;
                } else {
                    aggregate.distanceRight = observation.distanceRight;
                }
            }

            if (observation.distanceFront != null) {
                countFront++;
                if (aggregate.distanceFront != null) {
                    aggregate.distanceFront = (observation.distanceFront + (aggregate.distanceFront * (countFront - 1))) / countFront;
                } else {
                    aggregate.distanceFront = observation.distanceFront;
                }
            }

            if (observation.headingOffset != null) {
                countHeading++;
                if (aggregate.headingOffset != null) {
                    aggregate.headingOffset = (observation.headingOffset + (aggregate.headingOffset * (countHeading - 1))) / countHeading;
                } else {
                    aggregate.headingOffset = observation.headingOffset;
                }
            }
        }

        public synchronized void reset() {
            aggregate = null;
            countRight = 0;
            countFront = 0;
            countHeading = 0;
        }

        public TileEdgeObservation getAggregateObservation() {
            return aggregate;
        }

    }

}
