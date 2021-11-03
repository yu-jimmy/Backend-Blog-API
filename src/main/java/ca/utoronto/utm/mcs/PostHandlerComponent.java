package ca.utoronto.utm.mcs;

import dagger.Component;
import javax.inject.Singleton;

@Singleton
@Component(modules = DaggerModule.class)
public interface PostHandlerComponent {

    public PostHandler buildHandler();
}
