package com.salesforce.web;

import canvas.CanvasClient;
import canvas.CanvasRequest;
import canvas.SignedRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by jonglee on 5/27/18.
 */
@Controller
@EnableDiscoveryClient
public class CanvasController {
    @Value("${salesforce.canvas.client-secret}")
    private String clientSecret;

    @Autowired
    private DiscoveryClient discoveryClient;

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public ModelAndView home() {
        return new ModelAndView("redirect:" + "https://login.salesforce.com");
    }

    @RequestMapping(value = "/canvas", method = RequestMethod.POST)
    public String handlePost(@RequestParam("signed_request") String signedRequest, Model model) throws IOException {

        //find out what micro-services are deployed
        String responseBody = callOutToSalesforce(signedRequest);


        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {
        });
        List<ServiceInfo> entries = new ArrayList<ServiceInfo>();

        int totalSize = (Integer) map.get("totalSize");

        if (totalSize > 0) {
            List<Map<String, Object>> records = (List<Map<String, Object>>) map.get("records");
            for (Map<String, Object> record : records) {
                String name = (String) record.get("Name");

                //call out to micro-services registry
                List<ServiceInstance> serviceInstances = discoveryClient.getInstances(name);
                for (ServiceInstance serviceInstance : serviceInstances) {
                    entries.add(new ServiceInfo(name, serviceInstance.getUri().toString()));
                }
            }
        }

        model.addAttribute("entries", entries);
        return "home";
    }

    private String callOutToSalesforce(@RequestParam("signed_request") String signedRequest) {
        CanvasRequest canvasRequest = SignedRequest.verifyAndDecode(signedRequest, clientSecret);
        CanvasClient canvasClient = canvasRequest.getClient();

        String sfdcInstanceUrl = canvasClient.getInstanceUrl();
        String restUrl = sfdcInstanceUrl + "/services/data/v42.0/query/";

        HttpHeaders restHeaders = new HttpHeaders();
        restHeaders.setContentType(MediaType.APPLICATION_JSON);
        restHeaders.add("Authorization", "OAuth " + canvasClient.getOAuthToken());

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(restUrl)
                .queryParam("q", "SELECT Name FROM MicroService__c WHERE Deployed__c = TRUE");

        HttpEntity<?> restRequest = new HttpEntity<>(restHeaders);
        RestTemplate getRestTemplate = new RestTemplate();
        ResponseEntity<String> responseStr = getRestTemplate.exchange(builder.build().toString(),
                HttpMethod.GET, restRequest, String.class);


        return responseStr.getBody();
    }
}
