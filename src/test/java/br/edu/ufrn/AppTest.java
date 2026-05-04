package br.edu.ufrn;

import br.edu.ufrn.idw.PlatformThreadsIDWApp;
import br.edu.ufrn.idw.SerialIDWApp;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AppTest {

    @Test
    void serialInterpolationReturnsExpectedValue() {
        List<SerialIDWApp.Sensor> sensors = List.of(
                new SerialIDWApp.Sensor(1, 0.0, 0.0, 10.0),
                new SerialIDWApp.Sensor(2, 0.0, 10.0, 20.0),
                new SerialIDWApp.Sensor(3, 10.0, 0.0, 30.0),
                new SerialIDWApp.Sensor(4, 10.0, 10.0, 40.0)
        );

        double interpolated = SerialIDWApp.interpolate(sensors, 5.0, 5.0, 2.0);

        assertEquals(25.0, interpolated, 1.0e-9);
    }

    @Test
    void platformThreadsInterpolationReturnsExpectedValue() {
        List<PlatformThreadsIDWApp.Sensor> sensors = List.of(
                new PlatformThreadsIDWApp.Sensor(1, 0.0, 0.0, 10.0),
                new PlatformThreadsIDWApp.Sensor(2, 0.0, 10.0, 20.0),
                new PlatformThreadsIDWApp.Sensor(3, 10.0, 0.0, 30.0),
                new PlatformThreadsIDWApp.Sensor(4, 10.0, 10.0, 40.0)
        );

        double interpolated = PlatformThreadsIDWApp.interpolate(sensors, 5.0, 5.0, 2.0);

        assertEquals(25.0, interpolated, 1.0e-9);
    }
}
