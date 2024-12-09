package cs1302.api;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import com.google.gson.Gson;
import java.net.http.*;
import java.net.URI;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ApiApp extends Application {

    Stage stage;
    Scene scene;
    VBox root;
    HBox contentBox;
    VBox movieDetailsVBox;
    VBox streamingInfoVBox;
    TextFlow movieDetailsFlow;
    ImageView posterImageView;
    TextField searchField;
    Button searchButton;
    Label streamingInfoLabel;

    private static final String OMDB_API_KEY = "95c8a566";
    private static final String WATCHMODE_API_KEY = "ooDknqZZ8bPZHQwGJH4XHffdemFaw48553tdgE2Y";

    public ApiApp() {
        root = new VBox(20);
        contentBox = new HBox(20);
        movieDetailsVBox = new VBox(15);
        streamingInfoVBox = new VBox(15);

        String placeholderUrl = "https://via.placeholder.com/300x450.png?text=No+Image";
        Image placeholderImage = new Image(placeholderUrl);
        posterImageView = new ImageView(placeholderImage);
        posterImageView.setPreserveRatio(true);
        posterImageView.setFitWidth(300);

        streamingInfoLabel = new Label("Available to stream on:");
        streamingInfoLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        HBox searchBox = new HBox(10);
        searchField = new TextField();
        searchField.setPromptText("Enter a movie title");
        searchButton = new Button("Search");
        searchBox.getChildren().addAll(searchField, searchButton);
        searchBox.setAlignment(Pos.CENTER);

        movieDetailsVBox.setPadding(new javafx.geometry.Insets(10));
        streamingInfoVBox.setPadding(new javafx.geometry.Insets(10));
        contentBox.setPadding(new javafx.geometry.Insets(10));
        root.setPadding(new javafx.geometry.Insets(10));

        movieDetailsVBox.getChildren().addAll(posterImageView);
        streamingInfoVBox.getChildren().addAll(streamingInfoLabel);

        contentBox.getChildren().addAll(movieDetailsVBox, streamingInfoVBox);
        root.getChildren().addAll(searchBox, contentBox);
        root.setAlignment(Pos.TOP_CENTER);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Movie Info App");

        searchButton.setOnAction(event -> searchMovie());

        scene = new Scene(root, 700, 625);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setOnCloseRequest(event -> Platform.exit());
        stage.show();
    }

    private void searchMovie() {
        String movieTitle = searchField.getText();
        if (!movieTitle.isEmpty()) {
            try {
                String encodedTitle = java.net.URLEncoder.encode(movieTitle, "UTF-8");
                String omdbUrl = "http://www.omdbapi.com/?t=" + encodedTitle + "&apikey=" + OMDB_API_KEY;

                HttpRequest omdbRequest = HttpRequest.newBuilder()
                    .uri(URI.create(omdbUrl))
                    .GET()
                    .build();

                HttpResponse<String> omdbResponse = HttpClient.newHttpClient()
                    .send(omdbRequest, HttpResponse.BodyHandlers.ofString());

                Gson gson = new Gson();
                OMDbResponse omdbMovie = gson.fromJson(omdbResponse.body(), OMDbResponse.class);

                if ("True".equals(omdbMovie.Response)) {
                    String title = omdbMovie.Title;
                    String year = omdbMovie.Year;
                    String posterUrl = omdbMovie.Poster;

                    updateMovieDetails(title, year);

                    Image posterImage = new Image(posterUrl, false);
                    posterImageView.setImage(posterImage);

                    fetchStreamingInfo(movieTitle);
                } else {
                    movieDetailsFlow.getChildren().clear();
                    movieDetailsFlow.getChildren().add(new Text("Movie not found."));
                    posterImageView.setImage(null);
                    streamingInfoVBox.getChildren().clear();
                    streamingInfoVBox.getChildren().add(new Label("Streaming Information: Not available"));
                }
            } catch (Exception e) {
                movieDetailsFlow.getChildren().clear();
                movieDetailsFlow.getChildren().add(new Text("Error fetching movie data."));
                streamingInfoVBox.getChildren().clear();
                streamingInfoVBox.getChildren().add(new Label("Streaming Information: Not available"));
                e.printStackTrace();
            }
        }
    }

    private void fetchStreamingInfo(String movieTitle) {
        try {
            String encodedTitle = java.net.URLEncoder.encode(movieTitle, "UTF-8");

            String watchModeSearchUrl = "https://api.watchmode.com/v1/search/?apiKey=" +
                WATCHMODE_API_KEY + "&search_field=name&search_value=" + encodedTitle;

            HttpRequest watchModeRequest = HttpRequest.newBuilder()
                .uri(URI.create(watchModeSearchUrl))
                .GET()
                .build();

            HttpResponse<String> watchModeResponse = HttpClient.newHttpClient().send(watchModeRequest, HttpResponse.BodyHandlers.ofString());

            Gson gson = new Gson();
            WatchModeSearchResponse watchModeSearch = gson.fromJson(watchModeResponse.body(), WatchModeSearchResponse.class);

            if (watchModeSearch != null && watchModeSearch.title_results != null && !watchModeSearch.title_results.isEmpty()) {
                WatchModeMovie movieData = watchModeSearch.title_results.get(0);
                String movieId = movieData.id;

                String streamingUrl = "https://api.watchmode.com/v1/title/" + movieId +
                    "/sources/?apiKey=" + WATCHMODE_API_KEY;

                HttpRequest streamingRequest = HttpRequest.newBuilder()
                    .uri(URI.create(streamingUrl))
                    .GET()
                    .build();

                HttpResponse<String> streamingResponse = HttpClient.newHttpClient().send(streamingRequest, HttpResponse.BodyHandlers.ofString());

                WatchModeListing[] streamingData = gson.fromJson(streamingResponse.body(), WatchModeListing[].class);

                streamingInfoVBox.getChildren().clear();
                streamingInfoVBox.getChildren().add(streamingInfoLabel);

                if (streamingData != null && streamingData.length > 0) {
                    String[] targetPlatforms = {
                        "Netflix", "Hulu", "Prime Video", "Disney+",
                        "MAX", "Peacock", "Paramount Plus", "AppleTV", "YouTube"
                    };

                    Set<String> addedPlatforms = new HashSet<>();

                    for (WatchModeListing listing : streamingData) {
                        if (listing != null && listing.name != null && !listing.name.isEmpty()) {
                            // Check if the platform is in the target list and not already added
                            if (Arrays.asList(targetPlatforms).contains(listing.name) &&
                                addedPlatforms.add(listing.name)) {
                                HBox platformBox = new HBox(10);

                                // Platform logo
                                String logoUrl = getPlatformLogoUrl(listing.name);
                                if (logoUrl != null) {
                                    ImageView logoView = new ImageView(new Image(logoUrl));
                                    logoView.setFitWidth(70);
                                    logoView.setFitHeight(70);
                                    platformBox.getChildren().add(logoView);
                                }


                                // Platform name
                                Label platformLabel = new Label(listing.name);
                                platformLabel.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 14));
                                platformLabel.setAlignment(Pos.CENTER);
                                platformLabel.setMaxHeight(Double.MAX_VALUE);
                                platformBox.getChildren().add(platformLabel);

                                streamingInfoVBox.getChildren().add(platformBox);
                            }
                        }
                    }
                } else {
                    streamingInfoVBox.getChildren().add(new Label("No streaming info available"));
                }
            } else {
                streamingInfoVBox.getChildren().clear();
                streamingInfoVBox.getChildren().add(new Label("No streaming info available"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateMovieDetails(String title, String year) {
        Text titleBold = new Text("Title: ");
        titleBold.setStyle("-fx-font-weight: bold;");

        Text titleText = new Text(title + "\n");

        Text yearBold = new Text("Year: ");
        yearBold.setStyle("-fx-font-weight: bold;");

        Text yearText = new Text(year);

        movieDetailsFlow = new TextFlow(titleBold, titleText, yearBold, yearText);
        movieDetailsVBox.getChildren().clear();
        movieDetailsVBox.getChildren().addAll(posterImageView, movieDetailsFlow);
    }

    private String getPlatformLogoUrl(String platformName) {
        switch (platformName) {
        case "Netflix":
            return "https://static.vecteezy.com/system/resources/thumbnails/019/956/198/small_2x/netflix-transparent-netflix-free-free-png.png";
        case "Hulu":
            return "https://uxwing.com/wp-content/themes/uxwing/download/brands-and-social-media/hulu-icon.png";
        case "Prime Video":
            return "https://seeklogo.com/images/A/amazon-prime-video-logo-6BB6062D90-seeklogo.com.png";
        case "Disney+":
            return "https://images.squarespace-cdn.com/content/v1/6481e2427d99a120d7205507/da199d5e-e6ce-48c6-af20-0bb874e27b80/Button_SQUARE.png";
        case "MAX":
            return "https://logodownload.org/wp-content/uploads/2024/03/max-logo-0.png";
        case "Peacock":
            return "https://logodownload.org/wp-content/uploads/2022/12/peacock-logo-0.png";
        case "Paramount Plus":
            return "https://logodownload.org/wp-content/uploads/2021/03/paramount-plus-logo-0.png";
        case "AppleTV":
            return "https://logodownload.org/wp-content/uploads/2023/05/apple-tv-logo-0.png";
        case "YouTube":
            return "https://static.vecteezy.com/system/resources/thumbnails/018/930/575/small_2x/youtube-logo-youtube-icon-transparent-free-png.png";
        default:
            return null;
        }
    }


    public static void main(String[] args) {
        launch(args);
    }

    class OMDbResponse {
        String Title;
        String Year;
        String Poster;
        String Response;
    }

    class WatchModeSearchResponse {
        List<WatchModeMovie> title_results;
    }

    class WatchModeMovie {
        String id;
    }

    class WatchModeListing {
        String name;
    }
}
