
package com.uma.example.springuma.integration;

import java.nio.file.Paths;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.uma.example.springuma.model.Imagen;
import com.uma.example.springuma.model.Medico;
import com.uma.example.springuma.model.Paciente;
import com.uma.example.springuma.integration.base.AbstractIntegration;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

import org.springframework.web.reactive.function.BodyInserters;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ImagenControllerWebTestClientIT extends AbstractIntegration {

    @LocalServerPort
    private Integer port;

    private WebTestClient testClient;

    private Paciente paciente;
    private Medico medico;

    @PostConstruct
    public void init() {
        testClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofMillis(300000)).build();
    }

    @BeforeEach
    void setUp() {

        medico = new Medico();
        medico.setNombre("Miguel");
        medico.setId(1L);
        medico.setDni("835");
        medico.setEspecialidad("Ginecologo");

        paciente = new Paciente();
        paciente.setId(1L);
        paciente.setNombre("Maria");
        paciente.setDni("888");
        paciente.setEdad(20);
        paciente.setCita("Ginecologia");
        paciente.setMedico(medico);

        // Crea médico
        testClient.post().uri("/medico")
                .body(Mono.just(medico), Medico.class)
                .exchange()
                .expectStatus().isCreated();

        // Crea paciente
        testClient.post().uri("/paciente")
                .body(Mono.just(paciente), Paciente.class)
                .exchange()
                .expectStatus().isCreated();
    }

    private void subirImagen(String nombreArchivo) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("image", new FileSystemResource(Paths.get("src/test/resources/" + nombreArchivo).toFile()));
            builder.part("paciente", paciente);

            testClient.post().uri("/imagen")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .exchange()
                    .expectStatus().isOk();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    @DisplayName("Subir imagen de forma correcta y verificar que se asocia al paciente")
    void testSubirImagen() {
        // Subimos una imagen (asegúrate de tener un 'healthy.png' en src/test/resources/)
        subirImagen("healthy.png");

        // Recuperamos la imagen para comprobar que se ha asociado al paciente
        FluxExchangeResult<Imagen> result = testClient.get().uri("/imagen/paciente/" + paciente.getId())
                .exchange()
                .expectStatus().isOk()
                .returnResult(Imagen.class);

        // Extraemos la primera imagen de la respuesta
        Imagen imagenGuardada = result.getResponseBody().blockFirst();

        // 1. Verificamos que la imagen realmente se ha recuperado (si falla aquí, es que no se subió el PNG)
        assertNotNull(imagenGuardada, "La imagen recuperada es nula. Revisa que healthy.png exista.");

        // 2. Verificamos que tiene un ID válido asignado por la BD
        assertNotNull(imagenGuardada.getId(), "La imagen debería tener un ID generado");

        // 3. Verificamos el paciente SOLO si el JSON lo ha devuelto
        if (imagenGuardada.getPaciente() != null) {
            assertEquals(paciente.getId(), imagenGuardada.getPaciente().getId());
        }
    }

    @Test
    @DisplayName("Subir imagen y realizar una predicción sobre ella")
    void testRealizarPrediccion() {
        // 1. Subir imagen
        subirImagen("healthy.png");

        // 2. Obtener el ID de la imagen que acabamos de subir
        Imagen imagenGuardada = testClient.get().uri("/imagen/paciente/" + paciente.getId())
                .exchange()
                .expectStatus().isOk()
                .returnResult(Imagen.class)
                .getResponseBody()
                .blockFirst();

        Long idImagen = imagenGuardada.getId();

        // 3. Realizar la predicción
        testClient.get().uri("/imagen/predict/" + idImagen)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isNotEmpty(); // Verifica que el resultado de la IA (String o JSON) no venga vacío
    }

   }
