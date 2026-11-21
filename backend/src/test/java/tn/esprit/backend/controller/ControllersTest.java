package tn.esprit.backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.esprit.backend.entity.Entreprise;
import tn.esprit.backend.entity.Equipe;
import tn.esprit.backend.entity.Projet;
import tn.esprit.backend.entity.ProjetDetaille;
import tn.esprit.backend.exception.ResourceNotFoundException;
import tn.esprit.backend.service.IEntrepriseService;
import tn.esprit.backend.service.IEquipeService;
import tn.esprit.backend.service.IProjetDetailleService;
import tn.esprit.backend.service.IProjetService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ControllersTest {

    private final IEntrepriseService entrepriseService = mock(IEntrepriseService.class);
    private final IEquipeService equipeService = mock(IEquipeService.class);
    private final IProjetService projetService = mock(IProjetService.class);
    private final IProjetDetailleService projetDetailleService = mock(IProjetDetailleService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new EntrepriseController(entrepriseService),
                new EquipeController(equipeService),
                new ProjetController(projetService),
                new ProjetDetailleController(projetDetailleService)
        ).build();
    }

    @Test
    void entrepriseEndpoints() throws Exception {
        Entreprise entreprise = Entreprise.builder().id(1L).nom("Esprit").adresse("Tunis").build();
        when(entrepriseService.addEntreprise(any())).thenReturn(entreprise);
        when(entrepriseService.updateEntreprise(any())).thenReturn(entreprise);
        when(entrepriseService.getEntrepriseById(1L)).thenReturn(entreprise);
        when(entrepriseService.getAllEntreprises()).thenReturn(List.of(entreprise));
        String body = "{\"nom\":\"Esprit\",\"adresse\":\"Tunis\"}";

        mockMvc.perform(post("/entreprise/add").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nom").value("Esprit"));
        mockMvc.perform(put("/entreprise/update").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.adresse").value("Tunis"));
        mockMvc.perform(get("/entreprise/get/1")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        mockMvc.perform(get("/entreprise/all")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(delete("/entreprise/delete/1")).andExpect(status().isOk());

        verify(entrepriseService).deleteEntreprise(1L);
    }

    @Test
    void equipeEndpoints() throws Exception {
        Equipe equipe = Equipe.builder().id(2L).nom("Alpha").specialite("Backend").build();
        when(equipeService.addEquipe(any())).thenReturn(equipe);
        when(equipeService.updateEquipe(any())).thenReturn(equipe);
        when(equipeService.getEquipeById(2L)).thenReturn(equipe);
        when(equipeService.getAllEquipes()).thenReturn(List.of(equipe));
        when(equipeService.getEquipesByEntreprise(1L)).thenReturn(List.of(equipe));
        when(equipeService.assignEquipeToEntreprise(2L, 1L)).thenReturn(equipe);
        when(equipeService.assignEquipeToProjet(2L, 3L)).thenReturn(equipe);
        String body = "{\"nom\":\"Alpha\",\"specialite\":\"Backend\"}";

        mockMvc.perform(post("/equipe/add").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nom").value("Alpha"));
        mockMvc.perform(put("/equipe/update").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(get("/equipe/get/2")).andExpect(status().isOk()).andExpect(jsonPath("$.specialite").value("Backend"));
        mockMvc.perform(get("/equipe/all")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/equipe/by-entreprise/1")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(2));
        mockMvc.perform(put("/equipe/assign-entreprise/2/1")).andExpect(status().isOk());
        mockMvc.perform(put("/equipe/assign-projet/2/3")).andExpect(status().isOk());
        mockMvc.perform(delete("/equipe/delete/2")).andExpect(status().isOk());

        verify(equipeService).deleteEquipe(2L);
    }

    @Test
    void equipeAssignUnknownShouldReturn404() throws Exception {
        when(equipeService.assignEquipeToProjet(9L, 9L)).thenThrow(new ResourceNotFoundException("Equipe", 9L));

        mockMvc.perform(put("/equipe/assign-projet/9/9")).andExpect(status().isNotFound());
    }

    @Test
    void projetEndpoints() throws Exception {
        Projet projet = Projet.builder().id(3L).sujet("DevOps").build();
        when(projetService.addProjet(any())).thenReturn(projet);
        when(projetService.updateProjet(any())).thenReturn(projet);
        when(projetService.getProjetById(3L)).thenReturn(projet);
        when(projetService.getAllProjets()).thenReturn(List.of(projet));
        String body = "{\"sujet\":\"DevOps\"}";

        mockMvc.perform(post("/projet/add").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sujet").value("DevOps"));
        mockMvc.perform(put("/projet/update").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(get("/projet/get/3")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(3));
        mockMvc.perform(get("/projet/all")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(delete("/projet/delete/3")).andExpect(status().isOk());

        verify(projetService).deleteProjet(3L);
    }

    @Test
    void projetDetailleEndpoints() throws Exception {
        ProjetDetaille detail = ProjetDetaille.builder().id(4L).description("Pipeline").technologie("Jenkins")
                .coutProvisoire(1200.0).build();
        when(projetDetailleService.addProjetDetaille(any())).thenReturn(detail);
        when(projetDetailleService.updateProjetDetaille(any())).thenReturn(detail);
        when(projetDetailleService.getProjetDetailleById(4L)).thenReturn(detail);
        when(projetDetailleService.getAllProjetsDetailles()).thenReturn(List.of(detail));
        when(projetDetailleService.getProjetDetaillesByProjet(3L)).thenReturn(List.of(detail));
        when(projetDetailleService.assignProjetDetailleToProjet(4L, 3L)).thenReturn(detail);
        String body = "{\"description\":\"Pipeline\",\"technologie\":\"Jenkins\",\"coutProvisoire\":1200.0}";

        mockMvc.perform(post("/projet-detaille/add").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.technologie").value("Jenkins"));
        mockMvc.perform(put("/projet-detaille/update").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(get("/projet-detaille/get/4")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(4));
        mockMvc.perform(get("/projet-detaille/all")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/projet-detaille/by-projet/3")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(4));
        mockMvc.perform(put("/projet-detaille/assign-projet/4/3")).andExpect(status().isOk());
        mockMvc.perform(delete("/projet-detaille/delete/4")).andExpect(status().isOk());

        verify(projetDetailleService).deleteProjetDetaille(4L);
    }
}
