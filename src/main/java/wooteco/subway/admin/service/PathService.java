package wooteco.subway.admin.service;

import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.WeightedMultigraph;
import org.springframework.stereotype.Service;
import wooteco.subway.admin.domain.Line;
import wooteco.subway.admin.domain.LineStation;
import wooteco.subway.admin.domain.PathType;
import wooteco.subway.admin.domain.Station;
import wooteco.subway.admin.dto.PathRequest;
import wooteco.subway.admin.dto.PathResponse;
import wooteco.subway.admin.exception.StationNotFoundException;
import wooteco.subway.admin.exception.WrongPathException;
import wooteco.subway.admin.repository.LineRepository;
import wooteco.subway.admin.repository.StationRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class PathService {
    private LineRepository lineRepository;
    private StationRepository stationRepository;

    public PathService(LineRepository lineRepository, StationRepository stationRepository) {
        this.lineRepository = lineRepository;
        this.stationRepository = stationRepository;
    }

    public PathResponse calculatePath(PathRequest request) {
        List<Station> stations = stationRepository.findAll();
        List<Line> lines = lineRepository.findAll();
        List<LineStation> lineStations = lines
                .stream()
                .flatMap(line -> line.getStations()
                        .stream()
                        .filter(lineStation -> Objects.nonNull(lineStation.getPreStationId())))
                .collect(Collectors.toList());

        Long sourceId = findStationIdByName(stations, request.getSource());
        Long targetId = findStationIdByName(stations, request.getTarget());

        if (sourceId.equals(targetId)) {
            throw new WrongPathException();
        }
        List<Long> shortestPath = createShortestPath(lines, sourceId, targetId, request.getType());

        List<Station> pathStations = shortestPath.stream()
                .map(id -> findStationById(stations, id))
                .collect(Collectors.toList());

        List<LineStation> pathLineStations = extractPathLineStations(lineStations, shortestPath);
        int distance = pathLineStations.stream()
                .mapToInt(LineStation::getDistance)
                .sum();
        int duration = pathLineStations.stream()
                .mapToInt(LineStation::getDuration)
                .sum();
        return new PathResponse(pathStations, distance, duration);
    }

    private List<LineStation> extractPathLineStations(List<LineStation> lineStations, List<Long> shortestPath) {
        List<LineStation> pathLineStations = new ArrayList<>();
        for (int i = 1; i < shortestPath.size(); i++) {
            Long preStationId = shortestPath.get(i - 1);
            Long stationId = shortestPath.get(i);

            pathLineStations.add(lineStations.stream()
                    .filter(lineStation -> lineStation.isLineStationOf(preStationId, stationId))
                    .findFirst()
                    .orElseThrow(WrongPathException::new));
        }
        return pathLineStations;
    }

    private Station findStationById(List<Station> stations, Long id) {
        return stations.stream()
                .filter(station -> station.getId().equals(id))
                .findFirst()
                .orElseThrow(StationNotFoundException::new);
    }

    private List<Long> createShortestPath(List<Line> lines, Long source, Long target, PathType type) {
        WeightedMultigraph<Long, DefaultWeightedEdge> graph = new WeightedMultigraph<>(DefaultWeightedEdge.class);
        lines.stream()
                .flatMap(it -> it.getLineStationsId().stream())
                .forEach(graph::addVertex);
        lines.stream()
                .flatMap(line -> line.getStations().stream())
                .filter(lineStation -> Objects.nonNull(lineStation.getPreStationId()))
                .forEach(it -> graph.setEdgeWeight(graph.addEdge(it.getPreStationId(), it.getStationId()), type.getWeight(it)));
        DijkstraShortestPath<Long, DefaultWeightedEdge> dijkstraShortestPath = new DijkstraShortestPath<>(graph);
        try {
            return dijkstraShortestPath.getPath(source, target).getVertexList();
        } catch (IllegalArgumentException e) {
            throw new WrongPathException();
        }
    }

    private Long findStationIdByName(List<Station> stations, String name) {
        return stations
                .stream()
                .filter(station -> station.getName().equals(name))
                .map(Station::getId)
                .findFirst()
                .orElseThrow(StationNotFoundException::new);
    }
}
