package de.fearnixx.jeak.service.controller.testImpls;

import de.fearnixx.jeak.reflect.RequestBody;
import de.fearnixx.jeak.reflect.RequestMapping;
import de.fearnixx.jeak.reflect.RequestParam;
import de.fearnixx.jeak.reflect.RestController;
import de.fearnixx.jeak.service.controller.RequestMethod;

@RestController(endpoint = "/test", pluginId = "testPluginId")
public class SecondTestController {

    @RequestMapping(method = RequestMethod.GET, endpoint = "/hello")
    public DummyObject hello() {
        return new DummyObject("second", 20);
    }

    @RequestMapping(method =  RequestMethod.GET, endpoint = "/info/:name")
    public String returnSentInfo(@RequestParam(name = "name") String name) {
        return "second" + name;
    }

    @RequestMapping(method = RequestMethod.POST, endpoint = "/body")
    public String sendBody(@RequestBody(type = String.class, name = "string") String string) {
        return "second body " + string;
    }
}
