package br.ufmt.sd.chat.web.controller;

import br.ufmt.sd.chat.web.service.TcpClientGatewayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Controller MVC Spring Boot para a Interface Web Corporativa da Federação.
 */
@Controller
public class ChatWebController {

    @Autowired
    private TcpClientGatewayService gatewayService;

    // Lista dos 26 estados + Distrito Federal para o seletor da Federação
    private static final List<String> UF_LIST = Arrays.asList(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA",
            "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN",
            "RS", "RO", "RR", "SC", "SP", "SE", "TO", "BR"
    );

    @GetMapping("/")
    public String index(HttpSession session) {
        if (session.getAttribute("userId") != null) {
            return "redirect:/chat";
        }
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage(Model model, @RequestParam(value = "error", required = false) String error) {
        model.addAttribute("ufs", UF_LIST);
        if (error != null) {
            model.addAttribute("errorMessage", "Falha ao conectar no Servidor Broker TCP da Federação. Verifique se o servidor está rodando.");
        }
        return "login";
    }

    @PostMapping("/login")
    public String processLogin(@RequestParam("poder") String poder,
                               @RequestParam("uf") String uf,
                               @RequestParam("nome") String nome,
                               HttpSession session) {
        
        String cleanNome = nome.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
        if (cleanNome.isEmpty()) cleanNome = "agente";

        // Formato Federativo Padrão: <poder>-<uf>-<nome> (ex: exec-mt-pedro)
        String userId = poder.toLowerCase() + "-" + uf.toLowerCase() + "-" + cleanNome;

        boolean success = gatewayService.loginWebUser(userId);
        if (success) {
            session.setAttribute("userId", userId);
            session.setAttribute("poder", poder.toUpperCase());
            session.setAttribute("uf", uf.toUpperCase());
            session.setAttribute("nome", nome);
            return "redirect:/chat";
        } else {
            return "redirect:/login?error=true";
        }
    }

    @GetMapping("/chat")
    public String chatPage(HttpSession session, Model model) {
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        gatewayService.refreshUsersAndGroups(userId);
        TcpClientGatewayService.WebSessionState state = gatewayService.getSession(userId);

        model.addAttribute("userId", userId != null ? userId : "");
        model.addAttribute("poder", session.getAttribute("poder") != null ? session.getAttribute("poder") : "");
        model.addAttribute("uf", session.getAttribute("uf") != null ? session.getAttribute("uf") : "");
        model.addAttribute("nome", session.getAttribute("nome") != null ? session.getAttribute("nome") : "");

        if (state != null) {
            model.addAttribute("messages", state.getMessageFeed() != null ? state.getMessageFeed() : Collections.emptyList());
            model.addAttribute("onlineUsers", state.getOnlineUsersCache() != null ? state.getOnlineUsersCache() : Collections.emptyList());
            model.addAttribute("groups", state.getGroupsCache() != null ? state.getGroupsCache() : Collections.emptyList());
        } else {
            model.addAttribute("messages", Collections.emptyList());
            model.addAttribute("onlineUsers", Collections.emptyList());
            model.addAttribute("groups", Collections.emptyList());
        }

        return "chat";
    }

    @PostMapping("/send-msg")
    public String sendMessage(@RequestParam("targetId") String targetId,
                              @RequestParam("content") String content,
                              @RequestParam(value = "isGroup", defaultValue = "false") boolean isGroup,
                              HttpSession session) throws IOException {

        String userId = (String) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";

        if (content != null && !content.trim().isEmpty()) {
            if (isGroup) {
                gatewayService.sendGroupMessage(userId, targetId, content.trim());
            } else {
                gatewayService.sendDirectMessage(userId, targetId, content.trim());
            }
        }
        return "redirect:/chat";
    }

    @PostMapping("/send-file")
    public String sendFile(@RequestParam("targetId") String targetId,
                           @RequestParam("file") MultipartFile file,
                           HttpSession session) throws IOException {

        String userId = (String) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";

        if (file != null && !file.isEmpty()) {
            gatewayService.sendFile(userId, targetId, file.getOriginalFilename(), file.getBytes());
        }
        return "redirect:/chat";
    }

    @PostMapping("/create-group")
    public String createGroup(@RequestParam("groupId") String groupId,
                              @RequestParam("groupName") String groupName,
                              @RequestParam(value = "adminOnly", defaultValue = "false") boolean adminOnly,
                              @RequestParam(value = "restrictedPoder", defaultValue = "all") String restrictedPoder,
                              HttpSession session) throws IOException {

        String userId = (String) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";

        String cleanGrpId = groupId.trim().startsWith("grp-") ? groupId.trim() : "grp-" + groupId.trim();
        gatewayService.createGroup(userId, cleanGrpId, groupName.trim(), adminOnly, restrictedPoder);
        return "redirect:/chat";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
